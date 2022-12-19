/**
 * Copyright (C) 2021 Couchbase, Inc.
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */

package trycb.service;

import com.couchbase.client.core.error.DocumentNotFoundException;
import com.couchbase.client.core.msg.kv.DurabilityLevel;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.UpsertOptions;
import com.couchbase.client.java.query.QueryResult;
import com.couchbase.transactions.AttemptContext;
import com.couchbase.transactions.TransactionGetResult;
import com.couchbase.transactions.Transactions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import trycb.config.Booking;
import trycb.config.CreditUser;
import trycb.config.User;
import trycb.model.Result;
import trycb.repository.BookingRepository;
import trycb.repository.CreditUserRepository;
import trycb.repository.UserRepository;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

import static trycb.repository.CreditUserRepository.AGENCY_MEMBERS;

@Service
public class TenantUser {
    private final TokenService jwtService;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final CreditUserRepository creditUserRepository;
    private final InferCreditService inferCreditService;
    private final TransferCreditService transferCreditService;
    private final FlightPath flightPathService;
    private final Bucket userBucket;
    private final Bucket travelBucket;
    private final Transactions transactions;

    public TenantUser(
            TokenService tokenService,
            InferCreditService inferCreditService,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            CreditUserRepository creditUserRepository,
            TransferCreditService transferCreditService,
            FlightPath flightPathService,
            @Qualifier("userBucket") Bucket userBucket,
            @Qualifier("tsBucket") Bucket travelBucket,
            Transactions transactions

    ) {
        this.jwtService = tokenService;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.creditUserRepository = creditUserRepository;
        this.inferCreditService = inferCreditService;
        this.transferCreditService = transferCreditService;
        this.flightPathService = flightPathService;
        this.travelBucket = travelBucket;
        this.userBucket = userBucket;
        this.transactions = transactions;
    }

    @Value("${sqlite.using}")
    private boolean isUsingSqlite;

    /**
     * Try to log the given tenant user in.
     */
    public Result<Map<String, Object>> login(final String tenant, final String username, final String password) {
        UserRepository userRepository = this.userRepository.withScope(tenant);
        String queryType = String.format("KV get - scoped to %s.users: for password field in document %s", tenant, username);
        Optional<User> userHolder;
        try {
            userHolder = userRepository.findById(username);
        } catch (DocumentNotFoundException ex) {
            throw new AuthenticationCredentialsNotFoundException("Bad Username or Password");
        }
        User res = userHolder.get();
        if (BCrypt.checkpw(password, res.password)) {
            Map<String, Object> data = JsonObject.create().put("token", jwtService.buildToken(username)).toMap();
            return Result.of(data, queryType);
        } else {
            throw new AuthenticationCredentialsNotFoundException("Bad Username or Password");
        }
    }

    /**
     * Create a tenant user.
     */
    public Result<Map<String, Object>> createLogin(final String tenant, final String username, final String password, DurabilityLevel expiry) {
        UserRepository userRepository = this.userRepository.withScope(tenant);
        String passHash = BCrypt.hashpw(password, BCrypt.gensalt());
        User user = new User(username, passHash);
        user.setCredits(inferCreditService.inferCreditForUser());
        UpsertOptions options = UpsertOptions.upsertOptions();
        if (expiry.ordinal() > 0) {
            options.durability(expiry);
        }
        String queryType = String.format("KV insert - scoped to %s.users: document %s", tenant, username);
        try {
            userRepository.withOptions(options).save(user);
            Map<String, Object> data = JsonObject.create().put("token", jwtService.buildToken(username)).toMap();
            return Result.of(data, queryType);
        } catch (Exception e) {
            throw new AuthenticationServiceException("There was an error creating account");
        }
    }

    /*
     * Register a flight (or flights) for the given tenant user.
     * Test case 1: 1 Exception require ROLLBACK all transaction
     */
    @Transactional
    public Result<Map<String, Object>> registerFlightForUserOld(final String tenant, final String username, final JsonArray newFlights) {
        UserRepository userRepository = this.userRepository.withScope(tenant);
        BookingRepository bookingRepository = this.bookingRepository.withScope(tenant);
        Optional<User> userDataFetch;
        try {
            userDataFetch = userRepository.findById(username);
        } catch (DocumentNotFoundException ex) {
            throw new IllegalStateException();
        }
        User userData = userDataFetch.get();
        long remainingCredits = userData.getCredits();
        int price = 0;

        if (newFlights == null) {
            throw new IllegalArgumentException("No flights in payload");
        }

        JsonArray added = JsonArray.create();
        ArrayList<String> allBookedFlights = null;
        if (userData.getFlightIds() != null) {
            allBookedFlights = new ArrayList(Arrays.asList(userData.getFlightIds())); // ArrayList(Arrays.asList(newFlights));
        } else {
            allBookedFlights = new ArrayList<>(newFlights.size());
        }

        Map<String, Map<String, JsonObject>> updatedQuotas = new HashMap<>();

        for (Object newFlight : newFlights) {
            checkFlight(newFlight);

            JsonObject t = ((JsonObject) newFlight);
            t.put("bookedon", "try-cb-spring");

            Booking booking = new Booking(UUID.randomUUID().toString());
            booking.name = t.getString("name");
            booking.sourceairport = t.getString("sourceairport");
            booking.destinationairport = t.getString("destinationairport");
            booking.flight = t.getString("flight");
            booking.utc = t.getString("utc");
            booking.airlineid = t.getString("airlineid");
            booking.date = t.getString("date");
            booking.price = t.getInt("price");
            booking.day = t.getInt("day");
            bookingRepository.save(booking);
            allBookedFlights.add(booking.bookingId);
            added.add(t);

            price += t.getInt("price");
            remainingCredits -= t.getInt("price");

            changeUpdatedQuotas(updatedQuotas, t.getString("id"), t.getString("flight"), t.getString("utc"), t.getInt("day"));
        }
        // all this must be in one transaction

        userData.setFlightIds(allBookedFlights.toArray(new String[]{}));
        userData = userRepository.save(userData); // need to rolled back

        // inner transaction
        userData.setCredits(remainingCredits);
        userRepository.save(userData); // need to rolled back
        transferCreditService.transferCredit(creditUserRepository.getAgencyMember().getId(), price); // need to rolled back
        //

        if (remainingCredits < 0) { // check after save to investigate transaction processing
            throw new RuntimeException("Your credits is not enough!!!");
        }

        // inner transaction
        flightPathService.updateQuotas(updatedQuotas); // need to rolled back, concurrency control
        //

        JsonObject responseData = JsonObject.create().put("added", added);

        String queryType = String.format("KV update - scoped to %s.user: for bookings field in document %s", tenant, username);
        return Result.of(responseData.toMap(), queryType);
    }

    public Result<Map<String, Object>> registerFlightForUser(final String tenant, final String username, final JsonArray newFlights) {
        JsonArray added = JsonArray.create();

        Consumer<AttemptContext> transactionLogic = (Consumer<AttemptContext>) ctx -> {
            TransactionGetResult bookingUserTx = ctx.get(travelBucket.scope(tenant).collection("users"), username);
            JsonObject bookingUserDoc = bookingUserTx.contentAs(JsonObject.class);

            long remainingCredits = bookingUserDoc.getInt("credits");
            int price = 0;

            JsonArray allBookedFlights = bookingUserDoc.getArray("flightIds");
            if (allBookedFlights == null) {
                allBookedFlights = JsonArray.create();
            }

            Map<String, Map<String, JsonObject>> updatedQuotas = new HashMap<>();

            for (Object newFlight : newFlights) {
                checkFlight(newFlight);

                JsonObject t = ((JsonObject) newFlight);
                t.put("bookedon", "try-cb-spring");

                Booking booking = new Booking(UUID.randomUUID().toString());
                booking.name = t.getString("name");
                booking.sourceairport = t.getString("sourceairport");
                booking.destinationairport = t.getString("destinationairport");
                booking.flight = t.getString("flight");
                booking.utc = t.getString("utc");
                booking.airlineid = t.getString("airlineid");
                booking.date = t.getString("date");
                booking.price = t.getInt("price");
                booking.day = t.getInt("day");

                ctx.insert(travelBucket.scope(tenant).collection("bookings"), booking.bookingId, booking);

                added.add(t); // side effect

                price += t.getInt("price");
                remainingCredits -= t.getInt("price");

                allBookedFlights.add(booking.bookingId);

                changeUpdatedQuotas(updatedQuotas, t.getString("id"), t.getString("flight"), t.getString("utc"), t.getInt("day"));
            }

            bookingUserDoc.put("credits", remainingCredits);
            bookingUserDoc.put("flightIds", allBookedFlights);

            ctx.replace(bookingUserTx, bookingUserDoc);

            // transferCreditService.transferCredit(creditUserRepository.getAgencyMember().getId(), price);
            TransactionGetResult targetUserTx = ctx.get(userBucket.defaultCollection(), "agency_user_3");
            JsonObject targetUserDoc = targetUserTx.contentAs(JsonObject.class);

            targetUserDoc.put("credits", targetUserDoc.getInt("credits") + price);
            ctx.replace(targetUserTx, targetUserDoc);

            if (remainingCredits < 0) { // check after save to investigate transaction processing
                throw new RuntimeException("Your credits is not enough!!!");
            }

            for (Map.Entry<String, Map<String, JsonObject>> entry : updatedQuotas.entrySet()) {
                TransactionGetResult routeTx = ctx.get(travelBucket.scope("inventory").collection("route"), entry.getKey());
                JsonObject route = routeTx.contentAs(JsonObject.class);

                JsonArray schedules = route.getArray("schedule");

                List<JsonObject> objects = new ArrayList<>();
                for (int i = 0; i < schedules.size(); i++) {
                    JsonObject oldObject = schedules.getObject(i);

                    String k = oldObject.getString("flight") + "." + oldObject.getString("utc") + "." + oldObject.getInt("day");

                    JsonObject newObject = entry.getValue().get(k);
                    if (newObject != null) {
                        int modifiedQuota = oldObject.getInt("quota") - newObject.getInt("quota");
                        if (modifiedQuota < 0) {
                            throw new RuntimeException("Quota cannot negative!!!");
                        }
                        oldObject.put("quota", modifiedQuota);
                    }
                    objects.add(oldObject);
                }

                schedules = JsonArray.from(objects);

                route.put("schedule", schedules);

                ctx.replace(routeTx, route);
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        try {
            transactions.run(transactionLogic);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JsonObject responseData = JsonObject.create().put("added", added);

        String queryType = String.format("KV update - scoped to %s.user: for bookings field in document %s", tenant, username);
        return Result.of(responseData.toMap(), queryType);
    }

    private static void checkFlight(Object f) {
        if (f == null || !(f instanceof JsonObject)) {
            throw new IllegalArgumentException("Each flight must be a non-null object");
        }
        JsonObject flight = (JsonObject) f;
        if (!flight.containsKey("name") || !flight.containsKey("date") || !flight.containsKey("sourceairport")
                || !flight.containsKey("destinationairport")) {
            throw new IllegalArgumentException("Malformed flight inside flights payload");
        }
    }

    private static void changeUpdatedQuotas(Map<String, Map<String, JsonObject>> updatedQuotas, String routeId, String flight, String utc, Integer day) {
        String scheduleKey = flight + "." + utc + "." + day;

        Map<String, Object> kv = new HashMap<>();
        kv.put("flight", flight);
        kv.put("utc", utc);
        kv.put("day", day);
        kv.put("quota", 1);

        JsonObject schedule = JsonObject.from(kv);

        Map<String, JsonObject> schedules = updatedQuotas.get(routeId);

        if (schedules == null) {
            schedules = new HashMap<>();
            schedules.put(scheduleKey, schedule);
        } else {
            JsonObject object = schedules.get(scheduleKey);
            if (object == null) {
                object = schedule;
            } else {
                object = object.put("quota", object.getInt("quota") + 1);
            }
            schedules.put(scheduleKey, object);
        }

        updatedQuotas.put(routeId, schedules);
    }

    public Result<List<Map<String, Object>>> getFlightsForUser(final String tenant, final String username) {
        UserRepository userRepository = this.userRepository.withScope(tenant);
        BookingRepository bookingRepository = this.bookingRepository.withScope(tenant);
        Optional<User> userDoc;

        try {
            userDoc = userRepository.findById(username);
        } catch (DocumentNotFoundException ex) {
            return Result.of(Collections.emptyList());
        }
        User userData = userDoc.get();
        String[] flights = userData.getFlightIds();
        if (flights == null) {
            return Result.of(Collections.emptyList());
        }

        // The "flights" array contains flight ids. Convert them to actual objects.
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (String flightId : flights) {
            Optional<Booking> res;
            try {
                res = bookingRepository.findById(flightId);
            } catch (DocumentNotFoundException ex) {
                throw new RuntimeException("Unable to retrieve flight id " + flightId);
            }
            Map<String, Object> flight = res.get().toMap();
            results.add(flight);
        }

        String queryType = String.format("KV get - scoped to %s.user: for %d bookings in document %s", tenant,
                results.size(), username);
        return Result.of(results, queryType);
    }
}

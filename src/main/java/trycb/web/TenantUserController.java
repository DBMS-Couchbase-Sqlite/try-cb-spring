package trycb.web;

import com.couchbase.client.core.msg.kv.DurabilityLevel;
import com.couchbase.client.java.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trycb.model.Error;
import trycb.model.IValue;
import trycb.model.Result;
import trycb.service.TenantUser;
import trycb.service.TokenService;

import java.util.Map;

@RestController
@RequestMapping("/api/tenants")
public class TenantUserController {
    private final TenantUser tenantUserService;
    private final TokenService jwtService;

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantUserController.class);

    @Value("${storage.expiry:0}")
    private int expiry;

    @Autowired
    public TenantUserController(TokenService jwtService, TenantUser tenantUserService) {
        this.jwtService = jwtService;
        this.tenantUserService = tenantUserService;
    }

    @PostMapping(value = "/{tenant}/user/login")
    public ResponseEntity<? extends IValue> login(@PathVariable("tenant") String tenant, @RequestBody Map<String, String> loginInfo) {
        String user = loginInfo.get("user");
        String password = loginInfo.get("password");
        if (user == null || password == null) {
            return ResponseEntity.badRequest().body(new Error("User or password missing, or malformed request"));
        }

        try {
            return ResponseEntity.ok(tenantUserService.login(tenant, user, password));
        } catch (AuthenticationException e) {
            e.printStackTrace();
            LOGGER.error("Authentication failed with exception", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Error(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Failed with exception", e);
            return ResponseEntity.status(500).body(new Error(e.getMessage()));
        }
    }

    @PostMapping(value = "/{tenant}/user/signup")
    public ResponseEntity<? extends IValue> createLogin(@PathVariable("tenant") String tenant, @RequestBody String json) {
        JsonObject jsonData = JsonObject.fromJson(json);
        try {
            Result<Map<String, Object>> result = tenantUserService
                    .createLogin(
                            tenant,
                            jsonData.getString("user"),
                            jsonData.getString("password"),
                            DurabilityLevel.values()[expiry]
                    );
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (AuthenticationServiceException e) {
            e.printStackTrace();
            LOGGER.error("Authentication failed with exception", e);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new Error(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Failed with exception", e);
            return ResponseEntity.status(500).body(new Error(e.getMessage()));
        }
    }

    @PutMapping(value = "/{tenant}/user/{username}/flights")
    public ResponseEntity<? extends IValue> book(@PathVariable("tenant") String tenant, @PathVariable("username") String username, @RequestBody String json, @RequestHeader("Authorization") String authentication) {
        if (authentication == null || !authentication.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Error("Bearer Authentication must be used"));
        }
        JsonObject jsonData = JsonObject.fromJson(json);
        try {
            jwtService.verifyAuthenticationHeader(authentication, username);
            Result<Map<String, Object>> result = tenantUserService.registerFlightForUser(tenant, username, jsonData.getArray("flights"));
            return ResponseEntity.ok().body(result);
        } catch (IllegalStateException e) {
            e.printStackTrace();
            LOGGER.error("Failed with invalid state exception", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new Error("Forbidden, you can't book for this user"));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            LOGGER.error("Failed with invalid argument exception", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Error(e.getMessage()));
        }
    }

    @GetMapping(value = "/{tenant}/user/{username}/flights")
    public Object booked(@PathVariable("tenant") String tenant, @PathVariable("username") String username, @RequestHeader("Authorization") String authentication) {
        if (authentication == null || !authentication.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Error("Bearer Authentication must be used"));
        }
        try {
            jwtService.verifyAuthenticationHeader(authentication, username);
            return ResponseEntity.ok(tenantUserService.getFlightsForUser(tenant, username));
        } catch (IllegalStateException e) {
            e.printStackTrace();
            LOGGER.error("Failed with invalid state exception", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new Error("Forbidden, you don't have access to this cart"));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            LOGGER.error("Failed with invalid argument exception", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new Error("Forbidden, you don't have access to this cart"));
        }
    }

}

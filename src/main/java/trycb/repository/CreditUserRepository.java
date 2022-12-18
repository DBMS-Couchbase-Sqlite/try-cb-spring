package trycb.repository;

import com.couchbase.client.core.error.IndexExistsException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import trycb.config.CreditUser;

import java.util.List;
import java.util.Random;

@Repository("creditUserRepository")
public class CreditUserRepository {
    public static final int AGENCY_MEMBERS = 3;

    private final Cluster cluster;
    private final Bucket bucket;

    public CreditUserRepository(Cluster cluster, @Qualifier(value = "userBucket") Bucket bucket) {
        this.cluster = cluster;
        this.bucket = bucket;
    }

    public void initData() {
        try {
            final QueryResult result = cluster.query("CREATE PRIMARY INDEX default_col_index ON " + bucket.name()
                    + "." + bucket.defaultScope().name() + "."
                    + bucket.defaultCollection().name());
            Thread.sleep(5000);
        } catch (IndexExistsException e) {
            System.out.println(String.format("Collection's primary index already exists"));
        } catch (Exception e) {
            System.out.println(String.format("General error <%s> when trying to create index ", e.getMessage()));
        }

        try {
            cluster.queryIndexes().createPrimaryIndex(bucket.name());
            Thread.sleep(5000);
        } catch (Exception e) {
            System.out.println("Primary index already exists on bucket " + bucket.name());
        }

        QueryResult result = cluster.query("SELECT * FROM " + bucket.name());
        List<CreditUser> users = result.rowsAs(CreditUser.class);
        if (users.isEmpty()) {
            bucket.defaultCollection().insert("agency_user_1", new CreditUser("agency_user_1", "Hoàng Khánh", "Ly", 100));
            bucket.defaultCollection().insert("agency_user_2", new CreditUser("agency_user_2", "Võ", "Long", 0));
            bucket.defaultCollection().insert("agency_user_3", new CreditUser("agency_user_3", "Nguyễn Công", "Anh", 80));
        }

        /*
        cluster.query("DELETE FROM " + bucket.name());
        cluster.query("DELETE FROM " + bucket.name()
                + "." + bucket.defaultScope().name() + "."
                + bucket.defaultCollection().name()); */
    }

    public CreditUser getAgencyMember() {
        QueryResult result = cluster.query("SELECT * FROM " + bucket.name());
        List<JsonObject> objects = result.rowsAsObject();

        // List<CreditUser> users = result.rowsAs(CreditUser.class); failed ??

        int index = new Random().nextInt(AGENCY_MEMBERS - 1);
        return new CreditUser(objects.get(index));
    }
}

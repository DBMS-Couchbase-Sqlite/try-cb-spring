package trycb.config.cb;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.manager.bucket.BucketSettings;
import com.couchbase.client.java.manager.bucket.BucketType;
import com.couchbase.transactions.TransactionDurabilityLevel;
import com.couchbase.transactions.Transactions;
import com.couchbase.transactions.config.TransactionConfigBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CouchbaseConfig {
    private final Cluster cluster;

    public CouchbaseConfig(Cluster cluster) {
        this.cluster = cluster;
    }

    @Bean(name = "tsBucket")
    public Bucket getTravelSampleBucket() {
        return cluster.bucket("travel-sample");
    }

    @Bean(name = "userBucket")
    public Bucket getUserBucket() {
        String userBucket = "user_profile";

        if (!cluster.buckets().getAllBuckets().containsKey(userBucket)) {
            cluster.buckets().createBucket(
                    BucketSettings.create(userBucket).bucketType(BucketType.EPHEMERAL) //.ramQuotaMB(256)
            );
        }
        return cluster.bucket(userBucket);
    }

    @Bean
    public Transactions transactions() {
        return Transactions.create(
                cluster,
                TransactionConfigBuilder.create()
                        .durabilityLevel(TransactionDurabilityLevel.NONE)
                        .expirationTime(Duration.ofMillis(300000)) // 5 minutes
                        .keyValueTimeout(Duration.ofMillis(15000)) // 15 seconds
                        // The configuration can be altered here, but in most cases the defaults are fine.
                        .build()
        );
    }
}

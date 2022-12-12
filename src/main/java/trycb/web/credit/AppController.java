package trycb.web.credit;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryScanConsistency;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;
import trycb.config.CreditUser;

import java.util.List;

@Controller
public class AppController {
    private final Cluster cluster;
    private final Bucket bucket;

    public AppController(Cluster cluster, @Qualifier("userBucket") Bucket bucket) {
        this.cluster = cluster;
        this.bucket = bucket;
    }

    @GetMapping("/")
    public ModelAndView deleteBusinessIntelligence(ModelMap model) {
        try {
            List<CreditUser> users = cluster.query("Select p.* from " + bucket.name()
                    + "." + bucket.defaultScope().name() + "."
                    + bucket.defaultCollection().name() + " p", QueryOptions.queryOptions()
                    .scanConsistency(QueryScanConsistency.REQUEST_PLUS))
                    .rowsAs(CreditUser.class);

            model.addAttribute("users", users);
            if (!users.isEmpty()) {
                return new ModelAndView("index");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ModelAndView("loading");
    }
}

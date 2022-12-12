package trycb.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import trycb.service.Index;

@RestController
public class IndexController {
    @GetMapping(value = "/intro", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String index() {
        return Index.getInfo();
    }
}

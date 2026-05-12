package io.asterconfig.admin.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AsterEmbedPageController {

    @GetMapping(value = {
            "/aster/embed/config",
            "/aster/embed/config/query",
            "/aster/embed/config/releases",
            "/aster/embed/config/routes"
    }, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> configCanvas() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("aster-embed.html"));
    }
}

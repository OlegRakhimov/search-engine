package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class AppConfig {
    private List<SiteConfig> sites;

    public List<SiteConfig> getSites() {
        return sites != null ? sites : List.of();
    }
}

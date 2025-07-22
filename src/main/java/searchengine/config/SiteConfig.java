package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import lombok.Data;

@Setter
@Getter
@Data
public class SiteConfig {
    private String url;
    private String name;
}

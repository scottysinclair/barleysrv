package scott.barleysrv;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {


  @Bean
  public QueryResultMessageBodyWriter queryResultWriter() {
    return new QueryResultMessageBodyWriter();
  }

  @Bean
  public EntityResultMessageBodyWriter entityWriter() {
    return new EntityResultMessageBodyWriter();
  }

}

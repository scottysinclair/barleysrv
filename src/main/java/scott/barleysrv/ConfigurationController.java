package scott.barleysrv;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import scott.barleydb.api.specification.SpecRegistry;

@RestController
public class ConfigurationController {

  @Autowired
  private Environments environments;

  @RequestMapping(value = "/fromdb", method = RequestMethod.POST, consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
  public String addConfiguration(@RequestBody String properties) throws Exception {
    System.out.println(properties);
    try {
      SpecRegistry spec = environments.addFromDatabase(parseProperties(properties));
      return toString(spec);
    }
    catch(Exception x) {
      StringWriter sw = new StringWriter();
      x.printStackTrace(new PrintWriter(sw));
      return sw.toString();
    }
  }

  private Properties parseProperties(String properties) throws IOException {
    Properties props = new Properties();
    props.load(new StringReader(properties));
    return props;
  }

  private String toString(SpecRegistry spec) throws Exception {
    StringWriter sw = new StringWriter();
    JAXBContext jctx = JAXBContext.newInstance(SpecRegistry.class);
    Marshaller m = jctx.createMarshaller();
    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    m.marshal(spec, sw);
    return sw.toString();
  }

}
package scott.barleysrv;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import scott.barleydb.api.core.Environment;
import scott.barleydb.api.specification.SpecRegistry;
import scott.barleydb.bootstrap.EnvironmentDef;
import scott.barleydb.build.specgen.fromdb.FromDatabaseSchemaToSpecification;

@Component
public class Environments {

  private Map<String, EnvironmentDef> envDefs = new HashMap<>();
  private Map<String, Environment> envs = new HashMap<>();

  public Environment getEnvironment(String name) {
    return envs.get(name);
  }

  public Environment getFirst() {
    return envs.values().iterator().next();
  }

  public SpecRegistry getSpecRegistry(String environmentName) {
    return envDefs.get(environmentName).getFullSpecRegistry();
  }


  public SpecRegistry addFromDatabase(Properties properties) throws Exception {
    DataSource ds = toDataSource(properties);
    SpecRegistry spec = toSpecification(ds, properties);
    cacheOnDisk(spec, properties);
    EnvironmentDef envDef = toEnvironmentDef(ds, spec, properties);
    String envName = properties.getProperty("env.name");
    if (envName != null) {
      Environment env = envDef.create();
      envDefs.put(envName, envDef);
      envs.put(envName, env);
    }
    return spec;
  }

  private DataSource toDataSource(Properties props) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName(props.getProperty("jdbc.driver"));
    ds.setUrl(props.getProperty("jdbc.url"));
    ds.setUsername(props.getProperty("jdbc.user"));
    ds.setPassword(props.getProperty("jdbc.password"));
    return ds;
  }

  private SpecRegistry toSpecification(DataSource ds, Properties props) throws Exception {
    FromDatabaseSchemaToSpecification dbToSpec = new FromDatabaseSchemaToSpecification(props.getProperty("namespace"));
    String removePrefix = props.getProperty("remove.prefix");
    if (removePrefix != null) {
      dbToSpec.removePrefix(removePrefix);
    }
    return dbToSpec.generateSpecification(ds, props.getProperty("jdbc.schema"));
  }

  private void cacheOnDisk(SpecRegistry spec, Properties props) throws Exception {
    JAXBContext jctx = JAXBContext.newInstance(SpecRegistry.class);
    Marshaller m = jctx.createMarshaller();
    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    m.marshal(spec, System.out);
    m.marshal(spec, new File(System.getProperty("java.io.tmpdir") + "/" + props.getProperty("namespace") + "-ns.xml"));
  }

  private EnvironmentDef toEnvironmentDef(DataSource ds, SpecRegistry specRegistry, Properties props) throws Exception {
     EnvironmentDef builder = EnvironmentDef.build();
     builder.withDataSource(ds);
     builder.withNoClasses();
     builder.withSpecs(specRegistry);
     if (props.getProperty("ddl.drop") != null) {
       builder.withDroppingSchema(true);
     }
     if (props.getProperty("ddl.create") != null) {
       builder.withSchemaCreation(true);
     }
     return builder;
  }

}

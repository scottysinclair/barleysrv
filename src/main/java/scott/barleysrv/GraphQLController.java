package scott.barleysrv;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scott.barleydb.api.graphql.BarleyGraphQLSchema;

@RestController
public class GraphQLController {

  @Autowired
  private Environments envs;

  private Map<String, BarleyGraphQLSchema> schemasByNamespace = new HashMap<>();

  @RequestMapping(path = "/sdl/{env}/{namespace}", produces = MediaType.TEXT_PLAIN_VALUE)
  public String graphql(@PathVariable(value = "env") String envName, @PathVariable(value = "namespace") String namespace) {
    BarleyGraphQLSchema schema = getOrCreate(envName, namespace);
    return schema.getSdlString();
  }

  private synchronized BarleyGraphQLSchema getOrCreate(String environmentName, String namespace) {
    BarleyGraphQLSchema schema = schemasByNamespace.get(namespace);
    if (schema == null) {
      schemasByNamespace.put(namespace,
          schema = new BarleyGraphQLSchema(envs.getSpecRegistry(environmentName), envs.getEnvironment(environmentName), namespace, null));
    }
    return schema;
  }


}
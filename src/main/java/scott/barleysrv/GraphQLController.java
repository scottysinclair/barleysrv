package scott.barleysrv;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import scott.barleydb.api.graphql.BarleyGraphQLSchema;
import scott.barleydb.api.graphql.GraphQLContext;
import scott.barleydb.api.graphql.GraphQLExecutionException;

@RestController
public class GraphQLController {

  @Autowired
  private Environments envs;

  private Map<String, BarleyGraphQLSchema> schemasByNamespace = new HashMap<>();

  @RequestMapping(path = "/sdl/{env}/{namespace}", produces = MediaType.TEXT_PLAIN_VALUE)
  public String sdl(@PathVariable(value = "env") String envName, @PathVariable(value = "namespace") String namespace) {
    BarleyGraphQLSchema schema = getOrCreate(envName, namespace);
    return schema.getSdlString();
  }

  @RequestMapping(path = "/graphql/{env}/{namespace}",
                  method = RequestMethod.POST,
                  consumes = MediaType.APPLICATION_JSON_VALUE,
                  produces = MediaType.APPLICATION_JSON_VALUE)
  public Object query(
      @PathVariable(value = "env") String envName,
      @PathVariable(value = "namespace") String namespace,
      @RequestBody Map<String,Object> body) {

    BarleyGraphQLSchema schema = getOrCreate(envName, namespace);
    GraphQLContext ctx = schema.newContext();
    String query = (String)body.get("query");
    try {
      Object result = ctx.execute(query);
      return result;
    }
    catch(GraphQLExecutionException x) {
        return x.getErrors();
    }
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
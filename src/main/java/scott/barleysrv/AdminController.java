package scott.barleysrv;


import javax.websocket.server.PathParam;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import scott.barleydb.api.config.Definitions;
import scott.barleydb.api.config.EntityType;
import scott.barleydb.api.config.NodeType;
import scott.barleydb.api.core.Environment;
import scott.barleydb.api.core.types.JavaType;
import scott.barleydb.api.specification.EnumSpec;
import scott.barleydb.api.specification.EnumValueSpec;

/*
 * #%L
 * BarleyRS
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2016 Scott Sinclair
 *       <scottysinclair@gmail.com>
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
@RestController
public class AdminController {

    @Autowired
    private Environments envs;


    public void createEntityType() {

    }

    public void deleteEntityType() {

    }

    @RequestMapping(path = "/barleyrs/entitytypes", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode listEntityTypes() {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode result = mapper.createArrayNode();
        for (Definitions defs: envs.getFirst().getDefinitionsSet().getDefinitions()) {
            for (EntityType entityType: defs.getEntityTypes()) {
                ObjectNode et = mapper.createObjectNode();
                et.put("namespace", defs.getNamespace());
                et.put("fqn", entityType.getInterfaceName());
                et.put("simpleName", entityType.getInterfaceShortName());
                result.add( et );
            }
        }
        return result;
    }

    /**
     * Gets the entity type as a JSON schema.<br/>
     * The schema is compatibly extended with extra barley information.
     *
     */
    @RequestMapping(path = "/barleyrs/entitytypes/{namespace}/{entityType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getEntityTypeJsonSchema(
            @PathVariable("namespace") String namespace,
            @PathVariable("entityType") String entityTypeName,
            @RequestParam(value = "options", required = false) boolean withOptions) {

         Environment env = envs.getFirst();
         Definitions definitions = env.getDefinitions(namespace);
         EntityType entityType = definitions.getEntityTypeMatchingInterface(entityTypeName, true);

         ObjectMapper mapper = new ObjectMapper();
         ObjectNode response = mapper.createObjectNode();

         JsonNode jsonSchema = toJsonSchema(mapper, namespace, entityType);
         response.set("schema", jsonSchema);

         ObjectNode options = mapper.createObjectNode();
         if (withOptions) {
             ObjectNode fields = mapper.createObjectNode();
             for (NodeType nodeType: entityType.getNodeTypes()) {
                 ObjectNode optionsForNode = createOptionsForNode(nodeType, mapper);
                 if (optionsForNode != null) {
                     fields.set(nodeType.getName(), optionsForNode);
                 }
             }
             options.set("fields", fields);
         }
         response.set("options", options);
         return response;
    }

    /**
     * Right now this is the fields which will have autosuggest.
     * @param namespace
     * @param entityTypeName
     * @param withOptions
     * @return
     */
    @RequestMapping(path = "/barleyrs/entitytypes/{namespace}/{entityType}/detailedInfo", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getEntityTypeDetailedInfo(
            @PathParam("namespace") String namespace,
            @PathParam("entityType") String entityTypeName,
            @RequestParam("options") boolean withOptions) {

         Environment env = envs.getFirst();
         Definitions definitions = env.getDefinitions(namespace);
         EntityType entityType = definitions.getEntityTypeMatchingInterface(entityTypeName, true);

         ObjectMapper mapper = new ObjectMapper();
         ObjectNode response = mapper.createObjectNode();

         JsonNode jsonSchema = toJsonSchema(mapper, namespace, entityType);
         response.set("schema", jsonSchema);

         ObjectNode options = mapper.createObjectNode();
         if (withOptions) {
             ObjectNode fields = mapper.createObjectNode();
             for (NodeType nodeType: entityType.getNodeTypes()) {
                 ObjectNode optionsForNode = createOptionsForNode(nodeType, mapper);
                 if (optionsForNode != null) {
                     fields.set(nodeType.getName(), optionsForNode);
                 }
             }
             options.set("fields", fields);
         }
         response.set("options", options);
         return response;
    }


    private ObjectNode createOptionsForNode(NodeType nodeType, ObjectMapper mapper) {
//        if (nodeType.isPrimaryKey() && nodeType.getEntityType().getKeyGenSpec() == KeyGenSpec.FRAMEWORK) {
//            ObjectNode opt = mapper.createObjectNode();
//            opt.put("hidden", true);
//            return opt;
//        }
        if (nodeType.getColumnName() != null && nodeType.getRelationInterfaceName() != null) {
            /*
             * we are a FK relation to another entity
             */
            ObjectNode opt = mapper.createObjectNode();
            ObjectNode node = mapper.createObjectNode();
            EntityType et = nodeType.getEntityType().getDefinitions().getEntityTypeMatchingInterface(nodeType.getRelationInterfaceName(), true);
            node.put("namespace", et.getDefinitions().getNamespace());
            node.put("entitytype", et.getInterfaceName());
            opt.set("autosuggest", node);
            return opt;
        }
        if (nodeType.getEnumSpec() != null && nodeType.isMandatory()) {
            ObjectNode opt = mapper.createObjectNode();
            opt.put("nullOption", false);
            return opt;
        }
        return null;
    }

    private JsonNode toJsonSchema(ObjectMapper mapper, String namespace, EntityType entityType) {
        ObjectNode schemaRoot = mapper.createObjectNode();

        Environment env = envs.getFirst();

        schemaRoot.put("title", "JSON Schema for namepsace " + namespace + " and entity " + entityType.getInterfaceName());
        schemaRoot.put("type", "object");
        /*
         * required section.
         */
        ArrayNode required = mapper.createArrayNode();
        for (NodeType nodeType: entityType.getNodeTypes()) {
            if (nodeType.isMandatory()) {
                required.add( nodeType.getName() );
            }
        }
        schemaRoot.set("required", required);

        /*
         * properties section
         */
        ObjectNode properties = mapper.createObjectNode();
        for (NodeType nodeType: entityType.getNodeTypes()) {
            if (nodeType.getEnumSpec() != null) {
                ObjectNode prop = mapper.createObjectNode();
                prop.put("type", toJSONSchemaType(nodeType.getJavaType()));
                prop.set("enum", enumValuesAsJsonArray(mapper, nodeType.getEnumSpec()));
                properties.set(nodeType.getName(), prop);
            }
            else if (nodeType.getJavaType() != null) {
                ObjectNode prop = mapper.createObjectNode();
                prop.put("type", toJSONSchemaType(nodeType.getJavaType()));
                properties.set(nodeType.getName(), prop);
            }
            else if (nodeType.getColumnName() != null && nodeType.getRelationInterfaceName() != null) {
                ObjectNode prop = mapper.createObjectNode();

                final EntityType relatedEntity = env.getDefinitions(namespace).getEntityTypeMatchingInterface(nodeType.getRelationInterfaceName(), true);
                final NodeType keyNodeType = relatedEntity.getNodeType(relatedEntity.getKeyNodeName(), true);
                prop.put("type", toJSONSchemaType(keyNodeType.getJavaType()));
                properties.set(nodeType.getName(), prop);
            }
        }
        schemaRoot.set("properties", properties);
        return schemaRoot;
   }

    private ArrayNode enumValuesAsJsonArray(ObjectMapper mapper, EnumSpec enumSpec) {
        ArrayNode enumValues = mapper.createArrayNode();
        for (EnumValueSpec ev: enumSpec.getEnumValues()) {
            enumValues.add( ev.getName() );
        }
        return enumValues;
    }

    private String toJSONSchemaType(JavaType javaType) {
        switch (javaType) {
        case STRING:
            return "string";
        case INTEGER:
            return "integer";
        case LONG:
            return "integer";
        case BOOLEAN:
            return "boolean";
        case BIGDECIMAL:
            return "number";
        case ENUM:
            return "string";
        case SQL_DATE:
          return "string";
        case UTIL_DATE:
          return "string";
        default:
            return "object";
        }
    }

}

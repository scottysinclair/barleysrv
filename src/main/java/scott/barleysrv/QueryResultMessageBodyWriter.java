package scott.barleysrv;

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

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import scott.barleydb.api.core.entity.Entity;
import scott.barleydb.api.core.entity.EntityContextState;
import scott.barleydb.api.core.entity.Node;
import scott.barleydb.api.core.entity.NotLoaded;
import scott.barleydb.api.core.entity.RefNode;
import scott.barleydb.api.core.entity.ToManyNode;
import scott.barleydb.api.core.entity.ValueNode;
import scott.barleydb.api.query.QJoin;
import scott.barleydb.api.query.QueryObject;
import scott.barleydb.server.jdbc.query.QueryResult;

public class QueryResultMessageBodyWriter implements HttpMessageConverter<QueryResult<?>> {

    private static final SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    private final List<String> namespaces;


    public QueryResultMessageBodyWriter() {
        System.out.println("IW NEW!!!");
        this.namespaces = new LinkedList<String>();
        namespaces.add("scott.picdb");
    }



    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
      return false;
    }



    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
      return clazz.equals(QueryResult.class);
    }



    @Override
    public List<org.springframework.http.MediaType> getSupportedMediaTypes() {
      return Collections.singletonList(MediaType.APPLICATION_JSON);
    }



    @Override
    public QueryResult<?> read(Class<? extends QueryResult<?>> clazz, HttpInputMessage inputMessage)
        throws IOException, HttpMessageNotReadableException {
      return null;
    }



    @Override
    public void write(QueryResult<?> result, MediaType contentType, HttpOutputMessage outputMessage)
        throws IOException, HttpMessageNotWritableException {

      System.out.println("converting QueryResult to JSON");
      result.getEntityContext().setEntityContextState(EntityContextState.INTERNAL);
      try {
          QueryObject<Object> qo = ThreadLocalHelper.get();
          ObjectMapper mapper = new ObjectMapper();
          JsonGenerator gen = mapper.getFactory().createGenerator(outputMessage.getBody());
          ArrayNode array = mapper.createArrayNode();
          for (Entity entity: result.getEntityList()) {
              Set<Entity> started = new HashSet<>();
              array.add( toJson(mapper, entity, qo, started) );

          }
          gen.writeTree(array);
      }
      finally {
          result.getEntityContext().setEntityContextState(EntityContextState.USER);
      }

    }


    private JsonNode toJson(ObjectMapper mapper, Entity entity, QueryObject<Object> qo, Set<Entity> started) {
        if (!started.add(entity)) {
            return null;
        }
        try {
            ObjectNode jsonEntity = mapper.createObjectNode();
            for (Node node: entity.getChildren(Node.class)) {
               putNode(mapper, jsonEntity, node, qo, started);
            }
            return jsonEntity;
        }
        finally {
            started.remove(entity);
        }
    }

    private void putNode(ObjectMapper mapper, ObjectNode jsonEntity, Node node, QueryObject<Object> qo, Set<Entity> started) {
        if (node instanceof ValueNode) {
            setValue(jsonEntity, node.getName(), (ValueNode)node);
        }
        else if (node instanceof RefNode) {
            if (!((RefNode)node).isLoaded()) {
                return;
            }
            Entity reffedEntity = ((RefNode) node).getReference(false);
            if (reffedEntity != null) {
                if (reffedEntity.isFetchRequired()){
                    /*
                     * a fetch is required, we just output the ID
                     */
                    jsonEntity.put(node.getName(), reffedEntity.getKey().getValue().toString());
                }
                else {
                    QJoin joinToNode = getJoin(qo, node);
                    if (joinToNode != null) {
                        /*
                         * we have the entities properties so we convert it to a json object
                         */
                        JsonNode je = toJson(mapper, reffedEntity, (QueryObject<Object>)joinToNode.getTo(), started);
                        if (je != null) {
                            jsonEntity.set(node.getName(), je);
                        }
                    }
                    else {
                        /*
                         * query did not join to the node, so even if it is fetched we return the PK
                         */
                        jsonEntity.put(node.getName(), reffedEntity.getKey().getValue().toString());
                    }
                }
            }
            else {
                jsonEntity.putNull(node.getName());
            }
        }
        else if (node instanceof ToManyNode) {
            ToManyNode tm = (ToManyNode)node;
            if (!tm.getList().isEmpty()) {
                ArrayNode array = jsonEntity.arrayNode();
                QJoin joinToNode = getJoin(qo, node);
                for (Entity e: tm.getList()) {
                    JsonNode je = toJson(mapper, e, (QueryObject<Object>)joinToNode.getTo(), started);
                    if (je != null) {
                        array.add( je );
                    }
                }
                jsonEntity.set(tm.getName(), array);
            }
        }
    }

    private QJoin getJoin(QueryObject<Object> qo, Node node) {
        if (qo == null) {
            return null;
        }
        for (QJoin join: qo.getJoins()) {
            if (join.getFkeyProperty().equals(node.getName())) {
                return join;
            }
        }
        return null;
    }

    private void setValue(ObjectNode jsonEntity, String name, ValueNode node) {
        Object value = node.getValue();
        if (value == null) {
            jsonEntity.putNull(node.getName());
        }
        else if (value instanceof String) {
            jsonEntity.put(node.getName(), (String)value);
        }
        else if (value instanceof Long) {
            jsonEntity.put(node.getName(), (Long)value);
        }
        else if (value instanceof Date) {
            jsonEntity.put(node.getName(), df.format((Date)value));
        }
        else if (value instanceof Integer) {
            jsonEntity.put(node.getName(), (Integer)value);
        }
        else if (value instanceof BigDecimal) {
            jsonEntity.put(node.getName(), (BigDecimal)value);
        }
        else if (value instanceof Boolean) {
            jsonEntity.put(node.getName(), (Boolean)value);
        }
        else if (value == NotLoaded.VALUE) {
            //we skip these properties
        }
        else {
            throw new IllegalStateException("Cannot serialize value of type " + value.getClass().getSimpleName());
        }
    }

}

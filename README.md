

http://localhost:8080/sdl/bill1/scott.billable

http://localhost:8080/entitytypes

#generic server
http://localhost:8000/#/?_k=xbczlp

curl --data-binary  "@billable.properties" -X POST -H "Content-Type: text/plain" http://localhost:8080/fromdb  && curl --data-binary  "@feedback-by-id.query" -X POST -H "Content-Type: application/json" http://localhost:8080/graphql/bill1/scott.billable

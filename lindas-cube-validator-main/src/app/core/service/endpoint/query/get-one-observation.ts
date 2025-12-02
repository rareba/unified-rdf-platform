
export function getOneObservation(cubeIri: string) {
    return `
    PREFIX cube: <https://cube.link/>
  
    CONSTRUCT {
        <${cubeIri}> cube:observationSet ?set .
        ?set cube:observation ?s .
        ?s ?p ?o .
    }
    WHERE {
    {
        SELECT ?set WHERE {
            <${cubeIri}> cube:observationSet ?set .
        } LIMIT 1
    }
    { 
        SELECT ?s WHERE {
          <${cubeIri}> cube:observationSet/cube:observation ?s .
        } LIMIT 1
      }
      ?s ?p ?o
    }`;

}

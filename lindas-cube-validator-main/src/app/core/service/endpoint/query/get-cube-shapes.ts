
export function getShapeGraphForCube(cubeIri: string) {
  return `
    #pragma describe.strategy cbd

    PREFIX cube: <https://cube.link/>

    DESCRIBE ?s ?cube
    WHERE {
		  BIND(<${cubeIri}> AS ?cube)
      OPTIONAL {
      ?cube cube:observationConstraint ?s .
      }
    }`;
}

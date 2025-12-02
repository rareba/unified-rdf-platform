
export const CONSTRUCT_CUBE_ITEMS = `
PREFIX cube: <https://cube.link/>
PREFIX schema: <http://schema.org/>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

construct {
    ?cube ?p ?o .
} where {
    {
        SELECT ?cube WHERE {
            ?cube a cube:Cube .
            FILTER NOT EXISTS { ?cube schema:expires ?expires }
        }
    }
    VALUES ?p { 
        rdf:type
        schema:name 
        schema:description 
        schema:datePublished
    }
    ?cube ?p ?o .
}
`;
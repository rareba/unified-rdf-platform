# TODO: Apache Parquet Support

## Status
**Not implemented.** `ParquetFormatHandler` is a placeholder that registers the
format as *advertised-but-unavailable* so the UI can render a "coming soon" chip
instead of silently accepting a file it cannot process.

The `DataFormat.PARQUET` enum value exists for forward-compatibility and Flyway
migration stability only. Uploads with a `.parquet` extension are rejected by
`DataService.uploadDataSource` with an explicit error.

## What's required to finish

1. **Dependencies** — add to `rdf-forge-data-service/pom.xml`:
   - `org.apache.parquet:parquet-avro` (brings in parquet-hadoop + avro)
   - `org.apache.hadoop:hadoop-common` (for `Configuration` / `Path`)
   - Shade or relocate Hadoop classes; expect ~40 MB of transitive deps.
2. **Handler implementation** — replace the stub in
   `io.rdfforge.data.format.handlers.ParquetFormatHandler`:
   - `preview`: open the file with `AvroParquetReader`, read up to N rows,
     project to `Map<String, Object>` via the Avro schema.
   - `analyze`: walk the file schema + run column statistics.
   - `readIterator`: stream records using `ParquetReader.read()` in a loop.
   - `write`: optional — accept a list of columns + types and write via
     `AvroParquetWriter`.
   - Flip `DataFormatInfo.available` to `true` and clear `unavailableReason`.
3. **Storage adapter** — Parquet readers want `Path` objects; our `InputStream`
   based `DataFormatHandler` API may need a new overload
   (`readFromStorage(DataSourceEntity)`) to avoid buffering large files to
   memory. Consider staging to a temp file first.
4. **Registry routing** — remove the `return false` guards on
   `supportsExtension` / `supportsMimeType` so the registry routes `.parquet`
   uploads to this handler.
5. **Dimension mapping** — Parquet columns carry rich type information (logical
   types, nested groups). Decide how to surface nested records to the Cube
   builder. At minimum flatten lists of primitives to JSON strings.
6. **Tests** — add `ParquetFormatHandlerTest` covering:
   - Round-trip write/read of a small Parquet file with int/string/boolean/date
     columns.
   - Schema inference on a file produced by an external tool (e.g. `pyarrow`).
   - Large-file handling (use a 100k-row fixture generated in test setup).

## Security
The Parquet reader pulls a native schema; ensure that untrusted user uploads
cannot trigger `MAX_MEMORY`-related DOS. Consider capping row count during
analyze.

## Out of scope for this issue
- Arrow format (`.arrow`) — separate handler.
- Parquet writes from pipeline output — separate work item, coordinate with
  `rdf-forge-engine` once read side lands.

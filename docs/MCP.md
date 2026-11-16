# Remote MCP integration

OrbitForge supports a single configured remote MCP endpoint over HTTPS using Streamable HTTP-style JSON-RPC. It initializes the session, discovers up to 50 tools, and exposes them to the agent with sanitized function names. A bearer token is stored encrypted separately from API/GitHub credentials.

Every external MCP tool call requires explicit user approval. Tool results are treated as untrusted data, passed through credential redaction, and bounded to 20,000 characters before being added to the agent conversation. Remote HTTP and JSON-RPC error details are redacted, normalized, and capped at 300 characters. Remote tool names are limited to 128 characters and reject control characters. Tool descriptions and nested schema descriptions are credential-redacted, labeled as untrusted metadata, and capped at 500 characters; the agent is instructed not to follow instructions embedded in remote metadata. These controls are defense-in-depth, not a complete prompt-injection defense. Local file tools still enforce the app-private workspace boundary.

Automatic HTTP redirects are disabled for MCP requests, including HTTPS-to-HTTP redirects. This prevents a server-controlled redirect from silently changing the approved endpoint or downgrading transport for a request carrying the MCP bearer token. Configure the final HTTPS endpoint directly; a 3xx response is reported as an endpoint error instead of being followed.

Limitations: this is remote HTTP MCP only, not stdio/local-server support. Some servers require OAuth, SSE behavior, protocol versions, or authentication schemes not covered by this initial adapter. Verify against the specific server before relying on it.

# Remote MCP integration

OrbitForge supports a single configured remote MCP endpoint over HTTPS using Streamable HTTP-style JSON-RPC. It initializes the session, discovers up to 50 tools, and exposes them to the agent with sanitized function names. A bearer token is stored encrypted separately from API/GitHub credentials.

Every external MCP tool call requires explicit user approval. Tool results are treated as untrusted data and bounded to 20,000 characters. Local file tools still enforce the app-private workspace boundary.

Limitations: this is remote HTTP MCP only, not stdio/local-server support. Some servers require OAuth, SSE behavior, protocol versions, or authentication schemes not covered by this initial adapter. Verify against the specific server before relying on it.

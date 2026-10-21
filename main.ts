import { serveDir } from "https://deno.land";

Deno.serve((req) => {
  const url = new URL(req.url);
  
  // Debug helper: see exactly what file the browser is trying to ask for
  console.log(`[Request]: ${req.method} ${url.pathname}`);

  return serveDir(req, {
    // Tries to look into 'web', falls back to current folder if layout shifted
    fsRoot: "web", 
    showIndex: true,
    quiet: false
  }).catch((err) => {
    console.error("[Server Error]:", err);
    return new Response("Game file folder not found or accessible.", { status: 500 });
  });
});

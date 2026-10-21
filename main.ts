import { serveDir } from "https://deno.land";

Deno.serve((req) => {
  return serveDir(req, {
    fsRoot: "web",
    showIndex: true,
  });
});

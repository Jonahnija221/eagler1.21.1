import { serveDir } from "https://deno.land";

Deno.serve((req) => {
  return serveDir(req, {
    fsRoot: "web", // Points Deno to your web assets folder
    showIndex: true,
  });
});

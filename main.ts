// @ts-ignore: Built-in Deno server library
import { serveDir } from "jsr:@std/http@1.0.10/file-server";

Deno.serve((req) => {
  return serveDir(req, {
    fsRoot: "web",
    showIndex: true,
  });
});

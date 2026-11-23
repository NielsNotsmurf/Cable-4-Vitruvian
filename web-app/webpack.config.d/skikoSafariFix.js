// Wire the Skiko Safari white-screen workaround (see skiko-no-tail-call-loader.cjs).
// This file is inlined into build/wasm/packages/<app>/webpack.config.js
const path = require("path");

config.module.rules.push({
  test: /([\\/])skiko\.mjs$/,
  enforce: "pre",
  use: [
    {
      loader: path.resolve(
        __dirname,
        "../../../../web-app/webpack.config.d/skiko-no-tail-call-loader.cjs"
      ),
    },
  ],
});

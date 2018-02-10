# clj-spec-view [![GitHub license](https://img.shields.io/github/license/binaryage/chromex-sample.svg)](license.txt)

### An chome extension project that provide views of function specs at your browser (currently only github)

Just place the cursor over a speced function and see it's spec. (see a example at [Bigmouth repo](https://github.com/athos/Bigmouth/blob/master/src/bigmouth/interaction.clj)).

#### **clj-spec-view** project has following configuration:

  * uses [leiningen](http://leiningen.org) + [lein-cljsbuild](https://github.com/emezeske/lein-cljsbuild)
  * integrates [cljs-devtools](https://github.com/binaryage/cljs-devtools)
  * integrates [figwheel](https://github.com/bhauman/lein-figwheel) (for background page and popup buttons)
  * under `:unpacked` profile (development)
    * background page and popup button
      * compiles with `optimizations :none`
      * namespaces are included as individual files and source maps work as expected
      * figwheel works
    * content script
      * due to [security restrictions](https://github.com/binaryage/chromex-sample/issues/2), content script has to be provided as a single file
      * compiles with `:optimizations :whitespace` and `:pretty-print true`
      * figwheel cannot be used in this context (eval is not allowed)
  * under `:release` profile
    * background page, popup button and content script compile with `optimizations :advanced`
    * elides asserts
    * no figwheel support
    * no cljs-devtools support
    * `lein package` task is provided for building an extension package for release

### Local setup

#### Extension development

We assume you are familiar with ClojureScript tooling and you have your machine in a good shape running recent versions of
java, maven, leiningen, etc.

  * clone this repo somewhere:
    ```bash
    git clone https://github.com/pfeodrippe/clj-spec-view.git
    cd clj-spec-view
    ```
  * clj-spec-view gets built into `resources/unpacked/compiled` folder.

    In one terminal session run (will build background and popup pages using figwheel):
    ```bash
    lein fig
    ```
    In a second terminal session run (will auto-build content-script):
    ```bash
    lein content
    ```
  * use latest Chrome Canary with [Custom Formatters](https://github.com/binaryage/cljs-devtools#enable-custom-formatters-in-your-chrome-canary) enabled
  * In Chrome Canary, open `chrome://extensions` and add `resources/unpacked` via "Load unpacked extension..."

#### Extension packaging

[Leiningen project](project.clj) has defined "release" profile for compilation in advanced mode. Run:
```bash
lein release
```

This will build an optimized build into [resources/release](resources/release). You can add this folder via "Load unpacked extension..."
to test it.

When satisfied, you can run:
```bash
lein package
```

This will create a folder `releases/clj-spec-view-x.y.z` where x.y.z will be current version from [project.clj](project.clj).
This folder will contain only files meant to be packaged.

Finally you can use Chrome's "Pack extension" tool to prepare the final package (.crx and .pem files).

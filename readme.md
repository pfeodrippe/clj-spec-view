<div align="center">
  <h1 align="center">clj-spec-view</h1>
</div>

<div align="center"> 
  
[![GitHub license](https://img.shields.io/github/license/pfeodrippe/clj-spec-view.svg)](license.txt)
[![Chrome Web Store](https://img.shields.io/chrome-web-store/d/ohdilhfeehobpbnioeghljglgjbpjkin.svg)](https://chrome.google.com/webstore/detail/clj-spec-view/ohdilhfeehobpbnioeghljglgjbpjkin)
[![Chrome Web Store](https://img.shields.io/chrome-web-store/rating/ohdilhfeehobpbnioeghljglgjbpjkin.svg)](https://chrome.google.com/webstore/detail/clj-spec-view/ohdilhfeehobpbnioeghljglgjbpjkin)

 <strong>A chrome extension that provide views of function specs at your browser. (*currently only github*).</strong>
 <div align="center">
  <h3>
    <a href="https://chrome.google.com/webstore/detail/clj-spec-view/ohdilhfeehobpbnioeghljglgjbpjkin">
      Download
    </a>
    <span> | </span>
    <a href="https://github.com/pfeodrippe/clj-spec-view/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22">
      Contribute
    </a>
  </h3>
</div>
</div>

## How it works

Just place the cursor over a specified function and see its spec. 
![](demo.gif)

You can see some fspec examples at [Bigmouth repo](https://github.com/athos/Bigmouth/blob/master/src/bigmouth/interaction.clj).

### Github Access Token (for rate limits or private repos)

[Create one](https://help.github.com/articles/creating-an-access-token-for-command-line-use) and access it from the extension popup button (at your toolbar).

## TODO

- Add icon at code
- Search at actual branch
- Find clojure.alpha.spec alias (now it shows any `fdef` definition)
- Open file of current spec
- Add contribute guide

## Thanks

To all the contributors and the awesome [Chromex](https://github.com/binaryage/chromex) library.

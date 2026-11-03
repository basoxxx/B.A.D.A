// Versione e novità lette da GitHub Releases. Se la richiesta fallisce resta il testo statico della pagina.
(function () {
  var API = "https://api.github.com/repos/basoxxx/B.A.D.A/releases?per_page=4";
  var date = function (s) {
    return new Date(s).toLocaleDateString("it-IT", { day: "numeric", month: "long", year: "numeric" });
  };
  fetch(API, { headers: { Accept: "application/vnd.github+json" } })
    .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
    .then(function (list) {
      list = list.filter(function (r) { return !r.draft && !r.prerelease; });
      if (!list.length) return;
      var latest = list[0];
      var apk = (latest.assets || []).filter(function (a) { return a.name === "offtrack.apk"; })[0];
      var size = apk ? " · " + (apk.size / 1048576).toLocaleString("it-IT", { maximumFractionDigits: 1 }) + " MB" : "";
      document.getElementById("version").textContent =
        "Versione " + latest.tag_name.replace(/^v/, "") + ", " + date(latest.published_at) + size.replace(" · ", ", ") + ". Android 8.0 o successivo.";

      var box = document.getElementById("releases");
      var footer = box.lastElementChild;
      list.forEach(function (r) {
        // Le note sono il messaggio del commit: prima riga titolo, poi i paragrafi.
        var parts = (r.body || "").replace(/\r/g, "").split(/\n\s*\n/);
        var art = document.createElement("article");
        art.className = "release";
        var meta = document.createElement("p");
        meta.className = "when";
        meta.textContent = r.tag_name.replace(/^v/, "Versione ") + ", " + date(r.published_at);
        var h = document.createElement("h3");
        h.textContent = parts[0] || r.name;
        art.appendChild(meta);
        art.appendChild(h);
        parts.slice(1).forEach(function (para) {
          // Solo le parti per chi usa l'app: niente righe tecniche del commit.
          if (/^(Co-Authored-By|Claude-Session|Verifica|Dettagli tecnici):/m.test(para)) return;
          var p = document.createElement("p");
          p.textContent = para.replace(/\n/g, " ");
          art.appendChild(p);
        });
        box.insertBefore(art, footer);
      });
    })
    .catch(function () {});
})();

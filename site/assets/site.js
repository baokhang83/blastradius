// Interactive architecture diagram. Progressive enhancement: the page is fully readable without it.
(function () {
  var root = document.getElementById('arch');
  if (!root) return;

  var STEPS = {
    track: {
      title: 'Track — on the base branch',
      body: 'A <code>java.lang.instrument</code> agent watches every class actually loaded while each test runs and saves a commit-keyed index under <code>.blastradius/</code>: which test touched which production classes. Observed, not guessed. The full suite runs untouched.'
    },
    index: {
      title: 'Index — .blastradius/&lt;sha&gt;/index.json',
      body: 'The saved map. In CI, persist it with the ordinary cache (like <code>~/.m2</code>) or share it through an optional S3-compatible store. Credentials come from the standard AWS chain; none go in build files.'
    },
    diff: {
      title: 'Diff — merge base to HEAD',
      body: 'On any other build, Blastradius diffs from the Git merge base with <code>baseRef</code>. This isolates the PR’s own Java and Kotlin changes from commits that landed on the target branch after the branch diverged, and separates source from config, resources and build files.'
    },
    select: {
      title: 'Select — the rules',
      body: 'A test runs if one of its tracked dependencies changed, it is new or modified, an executed class reaches a changed class through one or two recorded static-invocation edges, or a non-source change triggered the conservative “run everything” fallback.'
    },
    gate: {
      title: 'Gate — standard Surefire / Gradle Test filter',
      body: 'The selection is handed to Surefire/Failsafe as a plain <code>-Dtest=</code> filter (Gradle: the <code>Test</code> task filter), so it coexists with JaCoCo and custom <code>argLine</code>. It changes which tests get the chance to fail, never what fails a build.'
    },
    fallback: {
      title: 'Fallback — no usable index',
      body: 'Missing, unreadable, incompatible or unreachable index? The full suite runs and the reason is printed (<code>MISSING</code>, <code>UNREADABLE</code>, <code>FORMAT_VERSION_MISMATCH</code>, <code>ANCHOR_UNREACHABLE</code>, …). Fallback is the plugin being honest, not an error.'
    }
  };

  // Which nodes/edges are lit for each build mode.
  var MODES = {
    track:    { nodes: ['track', 'index'], edges: ['e-track-index'], text: 'TRACK: the commit is baseRef. Full suite runs; the index is refreshed for next time.' },
    select:   { nodes: ['index', 'diff', 'select', 'gate'], edges: ['e-index-select', 'e-diff-select', 'e-select-gate'], text: 'SELECT: a usable index exists for the merge base. Surefire is narrowed.' },
    fallback: { nodes: ['diff', 'fallback', 'gate'], edges: ['e-diff-fallback', 'e-fallback-gate'], text: 'FALLBACK: no usable index. Full suite runs.' }
  };

  var nodes = root.querySelectorAll('.node');
  var edges = root.querySelectorAll('.edge');
  var detail = root.querySelector('.detail');
  var tabs = root.querySelectorAll('.tabs button');
  var current = 'select';

  function showDetail(id) {
    var s = STEPS[id];
    detail.innerHTML = '<h3>' + s.title + '</h3><p>' + s.body + '</p><p class="mode-text"></p>';
    detail.querySelector('.mode-text').textContent = MODES[current].text;
    nodes.forEach(function (n) { n.classList.toggle('active', n.dataset.step === id); });
  }

  function setMode(mode) {
    current = mode;
    tabs.forEach(function (b) { b.setAttribute('aria-pressed', String(b.dataset.mode === mode)); });
    var lit = MODES[mode];
    nodes.forEach(function (n) { n.classList.toggle('dim', lit.nodes.indexOf(n.dataset.step) < 0); });
    edges.forEach(function (e) { e.classList.toggle('dim', lit.edges.indexOf(e.id) < 0); });
    showDetail(lit.nodes[0]);
  }

  nodes.forEach(function (n) {
    n.setAttribute('tabindex', '0');
    n.setAttribute('role', 'button');
    n.addEventListener('click', function () { showDetail(n.dataset.step); });
    n.addEventListener('keydown', function (ev) {
      if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); showDetail(n.dataset.step); }
    });
  });
  tabs.forEach(function (b) { b.addEventListener('click', function () { setMode(b.dataset.mode); }); });
  setMode('select');
})();

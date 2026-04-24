/* ════════════════════════════════════════════════════════════════
   CONFIGURAÇÃO INICIAL - Marked.js, Sidebar, i18n UI
════════════════════════════════════════════════════════════════ */

function configureMarked() {
  const renderer = new marked.Renderer();
  renderer.code = (code, lang) => {
    const codeText = typeof code === 'object' ? code.text : code;
    const rawLang = typeof lang === 'string' ? lang : '';
    const langOnly = rawLang.includes(':') ? rawLang.split(':')[0] : rawLang;
    const filePath = rawLang.includes(':') ? rawLang.split(':').slice(1).join(':') : null;

    let language = langOnly && hljs.getLanguage(langOnly) ? langOnly : null;
    const result = language ? hljs.highlight(codeText, { language }) : hljs.highlightAuto(codeText);
    language = language || result.language || 'plaintext';
    const ll = language.toLowerCase();

    const filePathHtml = filePath
      ? `<span class="code-filepath" title="${escapeHtml(filePath)}">${escapeHtml(filePath)}</span>`
      : '';

    return `<pre><div class="code-header">${filePathHtml}<span class="code-lang">${ll}</span><button class="btn-copy-code" onclick="copyCode(this)">${t('message.copy')}</button></div><code class="hljs language-${ll}">${result.value}</code></pre>`;
  };
  marked.use({ renderer, breaks: true, gfm: true });
}

function setupSectionToggles() {
  ['recents-section-label', 'projects-section-label'].forEach(id => {
    const label = $(id);
    if (!label) return;
    label.addEventListener('click', e => {
      if (e.target.closest('.btn-section-add')) return;
      const targetId = id === 'recents-section-label' ? 'history-list' : 'projects-list';
      const list = $(targetId);
      const collapsed = label.classList.toggle('collapsed');
      list.style.display = collapsed ? 'none' : '';
    });
  });
}

function setupSidebarCollapse() {
  const collapsed = localStorage.getItem('oc-sidebar-collapsed') === 'true';
  if (collapsed) applySidebarCollapse(true, false);
  el.btnSidebarCollapse.addEventListener('click', () => {
    applySidebarCollapse(!el.sidebar.classList.contains('collapsed'));
  });
}

function applySidebarCollapse(collapse, animate = true) {
  if (!animate) el.sidebar.style.transition = 'none';
  el.sidebar.classList.toggle('collapsed', collapse);
  document.body.classList.toggle('sidebar-collapsed', collapse);
  localStorage.setItem('oc-sidebar-collapsed', collapse);
  if (!animate) setTimeout(() => (el.sidebar.style.transition = ''), 0);
}

/* ────────────────────────────────────────────────────────────
   Interface Language Selector
  Popula o <select id="param-ui-language"> com os idiomas
   disponíveis e sincroniza com o sistema de i18n.
────────────────────────────────────────────────────────────── */

function setupInterfaceLanguageSelector() {
  const select = el.interfaceLangSelect;
  if (!select) return;

  // Popula as opções
  const langs = getSupportedLanguages();
  select.innerHTML = langs.map(code =>
    `<option value="${code}" ${code === getCurrentLanguage() ? 'selected' : ''}>${getLanguageLabel(code)}</option>`
  ).join('');

  select.addEventListener('change', () => {
    setLanguage(select.value);
    // Reaplica traduções estáticas na UI sem recarregar a página
    applyStaticTranslations();
    // Reconstrói os módulos configurados (textos de setting, placeholders, etc.)
    applyDynamicTranslations();
  });
}

/**
 * Aplica traduções nos elementos estáticos da página que têm
 * o atributo data-i18n ou cujo texto pode ser trocado diretamente.
 * Isso cobre os textos que o usuário vê sem abrir modais.
 */
function applyStaticTranslations() {
  // --- Sidebar ---
  const searchInput = $('search-input');
  if (searchInput) searchInput.placeholder = t('sidebar.search');

  const historyEmpty = $('history-empty');
  if (historyEmpty) historyEmpty.querySelector('span').textContent = t('sidebar.noConversations');

  const projectsEmpty = $('projects-empty');
  if (projectsEmpty) projectsEmpty.querySelector('span').textContent = t('sidebar.noProjects');

  // Section labels
  const recentsLabel = $('recents-section-label');
  if (recentsLabel) {
    const span = recentsLabel.querySelector('span');
    if (span) span.textContent = t('sidebar.recents');
  }
  const projectsLabel = $('projects-section-label');
  if (projectsLabel) {
    const span = projectsLabel.querySelector('span');
    if (span) span.textContent = t('sidebar.projects');
  }

  // New chat button title
  const btnNewChat = $('btn-new-chat');
  if (btnNewChat) btnNewChat.title = t('sidebar.newChat');

  // --- Topbar ---
  const btnMemory = $('btn-memory');
  if (btnMemory) btnMemory.title = t('topbar.memory');
  const btnTheme = $('btn-theme');
  if (btnTheme) btnTheme.title = t('topbar.theme');
  const btnSettings = $('btn-settings');
  if (btnSettings) btnSettings.title = t('topbar.settings');

  // --- Input area ---
  const msgInput = $('message-input');
  if (msgInput) msgInput.placeholder = t('input.placeholder');
  const inputHint = document.querySelector('.input-hint');
  if (inputHint) inputHint.textContent = t('input.hint');

  const btnAttach = $('btn-attach');
  if (btnAttach && !btnAttach.disabled) btnAttach.title = t('input.attach');
  const btnWebSearch = $('btn-web-search');
  if (btnWebSearch) {
    const isActive = btnWebSearch.classList.contains('active');
    btnWebSearch.title = isActive ? t('input.webSearchActive') : t('input.webSearch');
  }
  const btnCodeMode = $('btn-code-mode');
  if (btnCodeMode) {
    const isActive = btnCodeMode.classList.contains('active');
    btnCodeMode.title = isActive ? t('input.codeModeActive') : t('input.codeMode');
  }
  const btnGithub = $('btn-github');
  if (btnGithub) btnGithub.title = t('input.github');
  const btnSend = $('btn-send');
  if (btnSend) btnSend.title = t('input.send');

  // --- Welcome screen ---
  const welcomeSubtitle = document.querySelector('.welcome-subtitle');
  if (welcomeSubtitle) welcomeSubtitle.innerHTML = t('welcome.subtitle').replace('\n', '<br/>');

  // Suggestion chips (data-prompt fica em pt-BR por serem apenas exemplos de conteúdo)
  const chips = document.querySelectorAll('.suggestion-chip');
  const chipKeys = ['welcome.chip.patterns', 'welcome.chip.python', 'welcome.chip.threads', 'welcome.chip.binarySearch'];
  chips.forEach((chip, i) => {
    if (chipKeys[i]) chip.textContent = t(chipKeys[i]);
  });

  // --- Settings panel ---
  const settingsTitle = document.querySelector('.settings-panel .settings-title');
  if (settingsTitle) settingsTitle.textContent = t('settings.model');

  const paramSystem = $('param-system');
  if (paramSystem) paramSystem.placeholder = t('settings.systemPromptPlaceholder');

  const btnModelInfo = $('btn-model-info');
  if (btnModelInfo) {
    const svgEl = btnModelInfo.querySelector('svg');
    btnModelInfo.textContent = t('settings.modelInfo');
    if (svgEl) btnModelInfo.insertBefore(svgEl, btnModelInfo.firstChild);
  }

  // Confirm modal
  const confirmTitle = document.querySelector('.confirm-title');
  if (confirmTitle) confirmTitle.textContent = t('confirm.title');
  const confirmSub = document.querySelector('.confirm-subtitle');
  if (confirmSub) confirmSub.textContent = t('confirm.subtitle');
  const confirmCancel = $('confirm-cancel');
  if (confirmCancel) confirmCancel.textContent = t('confirm.cancel');
  const confirmDelete = $('confirm-delete');
  if (confirmDelete) confirmDelete.textContent = t('confirm.delete');

  // Rename modal
  const renameTitle = document.querySelector('.rename-modal .modal-title');
  if (renameTitle) renameTitle.textContent = t('rename.title');
  const renameInput = $('rename-input');
  if (renameInput) renameInput.placeholder = t('rename.placeholder');
  const renameSave = $('rename-confirm');
  if (renameSave) renameSave.textContent = t('rename.save');
  const renameCancel = $('rename-cancel');
  if (renameCancel) renameCancel.textContent = t('rename.cancel');

  // Memory modal
  const memoryModalTitle = document.querySelector('#memory-modal .modal-title');
  if (memoryModalTitle) memoryModalTitle.textContent = t('memory.title');
  const memoryDesc = document.querySelector('.memory-desc');
  if (memoryDesc) memoryDesc.textContent = t('memory.desc');
  const memoryInput = $('memory-input');
  if (memoryInput) memoryInput.placeholder = t('memory.placeholder');
  const btnMemoryAdd = $('btn-memory-add');
  if (btnMemoryAdd) btnMemoryAdd.textContent = t('memory.add');

  // Memory categories
  const memCat = $('memory-category');
  if (memCat) {
    const catMap = ['general', 'preference', 'context', 'skill', 'project'];
    Array.from(memCat.options).forEach((opt, i) => {
      const key = catMap[i];
      if (key) opt.textContent = t(`memory.cat.${key}`);
    });
  }

  // Project modal form
  const projectNameLabel = document.querySelector('#project-form-section .project-form-label');
  if (projectNameLabel) projectNameLabel.textContent = t('project.nameLabel');
  const projectNameInput = $('project-name-input');
  if (projectNameInput) projectNameInput.placeholder = t('project.namePlaceholder');
  const projectFormSave = $('project-form-save');
  if (projectFormSave) projectFormSave.textContent = t('project.save');
  const projectFormCancel = $('project-form-cancel');
  if (projectFormCancel) projectFormCancel.textContent = t('project.cancel');

  const projectTextSave = $('project-text-save');
  if (projectTextSave) projectTextSave.textContent = t('project.save');
  const projectTextCancel = $('project-text-cancel');
  if (projectTextCancel) projectTextCancel.textContent = t('project.cancel');
  const projectTextName = $('project-text-name');
  if (projectTextName) projectTextName.placeholder = t('project.contextName');
  const projectTextContent = $('project-text-content');
  if (projectTextContent) projectTextContent.placeholder = t('project.contextContent');

  // Context badges
  const projectContextBadge = $('project-context-badge');
  const btnClearProject = $('btn-clear-project');
  if (btnClearProject) btnClearProject.title = t('project.clearContext');
  const btnClearGithub = $('btn-clear-github');
  if (btnClearGithub) btnClearGithub.title = t('github.disconnect');

  // GitHub modal
  const githubModalTitle = document.querySelector('#github-backdrop .modal-title');
  if (githubModalTitle) {
    // Mantém o SVG e troca só o texto
    const svgEl = githubModalTitle.querySelector('svg');
    githubModalTitle.textContent = t('github.title');
    if (svgEl) githubModalTitle.insertBefore(svgEl, githubModalTitle.firstChild);
  }
  const githubRepoInput = $('github-repo-input');
  if (githubRepoInput) githubRepoInput.placeholder = t('github.repoPlaceholder');
  const githubBranchInput = $('github-branch-input');
  if (githubBranchInput) githubBranchInput.placeholder = t('github.branchPlaceholder');
  const githubTokenInput = $('github-token-input');
  if (githubTokenInput) githubTokenInput.placeholder = t('github.tokenPlaceholder');
  const githubHint = document.querySelector('.github-form-hint');
  if (githubHint) githubHint.textContent = t('github.hint');
  const githubPrivateLabel = document.querySelector('.github-form-footer .toggle-label');
  if (githubPrivateLabel) {
    const input = githubPrivateLabel.querySelector('input');
    const track = githubPrivateLabel.querySelector('.toggle-track');
    githubPrivateLabel.textContent = t('github.private');
    if (input) githubPrivateLabel.insertBefore(input, githubPrivateLabel.firstChild);
    if (track) {
      githubPrivateLabel.insertBefore(track, githubPrivateLabel.children[1]);
    }
  }
  const btnGithubConnect = $('btn-github-connect');
  if (btnGithubConnect && btnGithubConnect.textContent !== t('github.connecting')) {
    btnGithubConnect.textContent = t('github.connect');
  }

  // Code panel
  const codePanelTitle = document.querySelector('.code-panel-title');
  if (codePanelTitle) {
    const svgEl = codePanelTitle.querySelector('svg');
    codePanelTitle.textContent = t('code.files');
    if (svgEl) codePanelTitle.insertBefore(svgEl, codePanelTitle.firstChild);
  }
  const codeDiffToggle = $('code-diff-toggle');
  if (codeDiffToggle && codeDiffToggle.textContent !== t('code.viewFile')) {
    codeDiffToggle.textContent = t('code.diff');
  }
  const codePanelDownloadFile = $('code-panel-download-file');
  if (codePanelDownloadFile) {
    const svgEl = codePanelDownloadFile.querySelector('svg');
    codePanelDownloadFile.textContent = t('code.downloadFile');
    if (svgEl) codePanelDownloadFile.insertBefore(svgEl, codePanelDownloadFile.firstChild);
  }
  const codePanelDownloadZip = $('code-panel-download-zip');
  if (codePanelDownloadZip) {
    const svgEl = codePanelDownloadZip.querySelector('svg');
    codePanelDownloadZip.textContent = t('code.downloadZip');
    if (svgEl) codePanelDownloadZip.insertBefore(svgEl, codePanelDownloadZip.firstChild);
  }
  const codeEditorFilename = $('code-editor-filename');
  if (codeEditorFilename && codeEditorFilename.textContent === 'Selecione um arquivo') {
    codeEditorFilename.textContent = t('code.selectFile');
  }

  // Settings interface language label
  const ifLangHint = document.querySelector('#param-ui-language ~ .settings-hint');
  if (ifLangHint) ifLangHint.textContent = t('settings.interfaceLanguageHint');
}

/**
 * Reaplica textos que dependem do idioma em elementos gerados dinamicamente
 * (coding overlay phrases, status bar, etc.)
 */
function applyDynamicTranslations() {
  // Reconfigura as frases do coding overlay
  if (typeof CODING_PHRASES !== 'undefined') {
    CODING_PHRASES.length = 0;
    CODING_PHRASES.push(
      t('coding.coding'),
      t('coding.analyzing'),
      t('coding.writing'),
      t('coding.organizing'),
      t('coding.applying'),
      t('coding.finishing')
    );
  }

  // Atualiza o status text se ainda estiver no estado padrão de conexão
  const statusText = $('status-text');
  if (statusText && statusText.textContent === 'Conectando...') {
    statusText.textContent = t('status.connecting');
  }
}
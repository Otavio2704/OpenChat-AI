/* ════════════════════════════════════════════════════════════════
   OpenChat — app.js
════════════════════════════════════════════════════════════════ */
const API = { BASE: 'http://localhost:8080' };

const state = {
  conversationId:   null,
  isStreaming:      false,
  model:            '',
  options:          {},
  systemPrompt:     '',
  language:         '',
  thinkingMode:     false,
  webSearchEnabled: false,
  capabilities:     null,
  pendingFiles:     [],
  abortController:  null,
  activeProjectId:  null,
  activeProjectName:'',
};

const $ = id => document.getElementById(id);
let el = {};

function initRefs() {
  el = {
    sidebar:           $('sidebar'),
    historyList:       $('history-list'),
    historyEmpty:      $('history-empty'),
    searchInput:       $('search-input'),
    modelSelect:       $('model-select'),
    modelTags:         $('model-tags'),
    messagesArea:      $('messages-area'),
    welcomeScreen:     $('welcome-screen'),
    messageInput:      $('message-input'),
    btnSend:           $('btn-send'),
    iconSend:          $('icon-send'),
    iconStop:          $('icon-stop'),
    btnNewChat:        $('btn-new-chat'),
    btnSettings:       $('btn-settings'),
    btnTheme:          $('btn-theme'),
    btnMemory:         $('btn-memory'),
    btnSidebarCollapse:$('btn-sidebar-collapse'),
    collapseIcon:      $('collapse-icon'),
    settingsPanel:     $('settings-panel'),
    btnModelInfo:      $('btn-model-info'),
    charCount:         $('char-count'),
    statusDot:         $('status-dot'),
    statusText:        $('status-text'),
    btnToggle:         $('btn-toggle-sidebar'),
    tempSlider:        $('param-temperature'),
    tempValue:         $('temperature-value'),
    ctxSlider:         $('param-ctx'),
    ctxValue:          $('ctx-value'),
    systemPrompt:      $('param-system'),
    languageSelect:    $('param-language'),
    thinkingChk:       $('param-thinking'),
    thinkingField:     $('thinking-field'),
    modalBackdrop:     $('modal-backdrop'),
    modalTitle:        $('modal-title'),
    modalBody:         $('modal-body'),
    modalClose:        $('modal-close'),
    confirmBackdrop:   $('confirm-backdrop'),
    confirmDelete:     $('confirm-delete'),
    confirmCancel:     $('confirm-cancel'),
    renameBackdrop:    $('rename-backdrop'),
    renameInput:       $('rename-input'),
    renameConfirm:     $('rename-confirm'),
    renameCancel:      $('rename-cancel'),
    renameClose:       $('rename-close'),
    memoryBackdrop:    $('memory-backdrop'),
    memoryModalClose:  $('memory-modal-close'),
    memoryInput:       $('memory-input'),
    memoryCategory:    $('memory-category'),
    btnMemoryAdd:      $('btn-memory-add'),
    memoryList:        $('memory-list'),
    accentSwatches:    $('accent-swatches'),
    customColorInput:  $('custom-color-input'),
    projectsList:         $('projects-list'),
    projectsEmpty:        $('projects-empty'),
    btnNewProject:        $('btn-new-project'),
    projectBackdrop:      $('project-backdrop'),
    projectModalTitle:    $('project-modal-title'),
    projectModalClose:    $('project-modal-close'),
    projectModalBody:     $('project-modal-body'),
    projectFormSection:   $('project-form-section'),
    projectDetailSection: $('project-detail-section'),
    projectNameInput:     $('project-name-input'),
    projectDescInput:     $('project-desc-input'),
    projectFormCancel:    $('project-form-cancel'),
    projectFormSave:      $('project-form-save'),
    projectDetailDesc:    $('project-detail-desc'),
    projectFilesList:     $('project-files-list'),
    btnProjectAddFile:    $('btn-project-add-file'),
    btnProjectAddText:    $('btn-project-add-text'),
    projectFileInput:     $('project-file-input'),
    projectTextAdd:       $('project-text-add'),
    projectTextName:      $('project-text-name'),
    projectTextContent:   $('project-text-content'),
    projectTextCancel:    $('project-text-cancel'),
    projectTextSave:      $('project-text-save'),
    btnProjectNewChat:    $('btn-project-new-chat'),
    btnProjectDelete:     $('btn-project-delete'),
    projectChatsSection:  $('project-chats-section'),
    projectChatsList:     $('project-chats-list'),
    projectContextBadge:  $('project-context-badge'),
    projectContextName:   $('project-context-name'),
    btnClearProject:      $('btn-clear-project'),
    btnAttach:            $('btn-attach'),
    btnWebSearch:         $('btn-web-search'),
    fileInput:            $('file-input'),
    attachPreview:        $('attach-preview'),
  };
}

/* ════════════════════════════════════════════════════════════════
   INIT
════════════════════════════════════════════════════════════════ */
async function init() {
  initRefs();
  setupEventListeners();
  configureMarked();
  loadPreferences();
  await Promise.all([loadModels(), loadHistory(), loadProjects()]);
  setupSectionToggles();
}

function configureMarked() {
  const renderer = new marked.Renderer();
  renderer.code = (code, lang) => {
    const codeText = typeof code === 'object' ? code.text : code;
    let language = lang && hljs.getLanguage(lang) ? lang : null;
    const result = language ? hljs.highlight(codeText, { language }) : hljs.highlightAuto(codeText);
    language = language || result.language || 'plaintext';
    const ll = language.toLowerCase();
    return `<pre><div class="code-header"><span class="code-lang">${ll}</span><button class="btn-copy-code" onclick="copyCode(this)">Copiar</button></div><code class="hljs language-${ll}">${result.value}</code></pre>`;
  };
  marked.use({ renderer, breaks: true, gfm: true });
}

/* ════════════════════════════════════════════════════════════════
   PREFERÊNCIAS
════════════════════════════════════════════════════════════════ */
function loadPreferences() {
  applyTheme(localStorage.getItem('oc-theme') || 'dark');
  applyAccent(localStorage.getItem('oc-accent') || '#00e5a0');
  const lang = localStorage.getItem('oc-language') || '';
  if (el.languageSelect) el.languageSelect.value = lang;
  state.language = lang;
}

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem('oc-theme', theme);
  const hljsLink = $('hljs-theme');
  if (hljsLink) hljsLink.href = theme === 'dark'
    ? 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark-dimmed.min.css'
    : 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css';
}

function applyAccent(color) {
  document.documentElement.style.setProperty('--accent', color);
  document.documentElement.style.setProperty('--accent-dim', color + 'b3');
  document.documentElement.style.setProperty('--accent-glow', color + '1f');
  localStorage.setItem('oc-accent', color);
  document.querySelectorAll('.swatch').forEach(s => s.classList.toggle('active', s.dataset.color === color));
  if (el.customColorInput) el.customColorInput.value = color;
}

/* ════════════════════════════════════════════════════════════════
   SIDEBAR
════════════════════════════════════════════════════════════════ */
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
  if (!animate) setTimeout(() => el.sidebar.style.transition = '', 0);
}

/* ════════════════════════════════════════════════════════════════
   CAPABILITIES
════════════════════════════════════════════════════════════════ */
async function loadCapabilities(modelName) {
  if (!modelName) return;
  updateModelTags(null, modelName);
  try {
    const res = await fetch(`${API.BASE}/api/models/${encodeURIComponent(modelName)}/capabilities`);
    if (!res.ok) throw new Error();
    const caps = await res.json();
    state.capabilities = caps;
    applyCapabilitiesToUI(caps);
    updateModelTags(caps, modelName);
  } catch {
    state.capabilities = null;
    applyCapabilitiesToUI(null);
    updateModelTags(null, modelName);
  }
}

function updateModelTags(caps, modelName) {
  if (!el.modelTags) return;
  const tags = [];
  const name = (modelName || '').toLowerCase();
  if (name.includes(':cloud') || name.includes('cloud'))
    tags.push('<span class="model-tag cloud">☁ Cloud</span>');
  if (caps?.supportsVision || name.includes('vision') || name.includes('llava'))
    tags.push('<span class="model-tag vision">👁 Vision</span>');
  if (caps?.supportsThinking || name.includes('qwen3') || name.includes('r1') || name.includes('qwq') || name.includes('deepseek'))
    tags.push('<span class="model-tag think">💭 Think</span>');
  if (name.includes('embed') || name.includes('embedding'))
    tags.push('<span class="model-tag embed">⊕ Embed</span>');
  if (name.includes('tools') || name.includes('tool'))
    tags.push('<span class="model-tag tools">🔧 Tools</span>');
  el.modelTags.innerHTML = tags.join('');
}

function applyCapabilitiesToUI(caps) {
  if (el.thinkingField) {
    const supported = caps?.supportsThinking ?? false;
    el.thinkingField.classList.toggle('disabled', !supported);
    el.thinkingChk.disabled = !supported;
    if (!supported) { el.thinkingChk.checked = false; state.thinkingMode = false; delete state.options.think; }
    el.thinkingField.title = supported ? '' : 'Este modelo não suporta Thinking Mode';
  }
  if (el.btnAttach) {
    el.btnAttach.disabled = false;
    const sv = caps?.supportsVision ?? false;
    el.fileInput.accept = sv ? '.pdf,.docx,.txt,.md,image/jpeg,image/png,image/webp,image/gif' : '.pdf,.docx,.txt,.md';
    el.btnAttach.title = sv ? 'Anexar arquivo ou imagem' : 'Anexar documento (PDF, DOCX, TXT, MD)';
  }
  if (caps?.contextLength > 0 && el.ctxSlider) {
    el.ctxSlider.max = caps.contextLength;
    const cur = Math.min(parseInt(el.ctxSlider.value), caps.contextLength);
    el.ctxSlider.value = cur; el.ctxValue.textContent = cur; state.options.num_ctx = cur;
  }
}

/* ════════════════════════════════════════════════════════════════
   EVENT LISTENERS
════════════════════════════════════════════════════════════════ */
function setupEventListeners() {
  setupSidebarCollapse();

  el.btnSend.addEventListener('click', () => { if (state.isStreaming) stopStreaming(); else sendMessage(); });
  el.messageInput.addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); if (!state.isStreaming) sendMessage(); }
  });
  el.messageInput.addEventListener('input', () => {
    el.messageInput.style.height = 'auto';
    el.messageInput.style.height = Math.min(el.messageInput.scrollHeight, 200) + 'px';
    updateCharCount();
  });

  el.btnNewChat.addEventListener('click', newConversation);
  el.btnToggle.addEventListener('click', () => el.sidebar.classList.toggle('open'));
  el.messagesArea.addEventListener('click', () => { if (window.innerWidth <= 768) el.sidebar.classList.remove('open'); });

  el.btnSettings.addEventListener('click', () => {
    el.settingsPanel.hidden = !el.settingsPanel.hidden;
    el.btnSettings.classList.toggle('active', !el.settingsPanel.hidden);
  });
  el.btnTheme.addEventListener('click', () =>
    applyTheme(document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark'));

  el.accentSwatches.addEventListener('click', e => {
    const s = e.target.closest('.swatch');
    if (s && s.dataset.color) applyAccent(s.dataset.color);
  });
  el.customColorInput.addEventListener('input', e => applyAccent(e.target.value));

  el.tempSlider.addEventListener('input', () => { el.tempValue.textContent = el.tempSlider.value; state.options.temperature = parseFloat(el.tempSlider.value); });
  el.ctxSlider.addEventListener('input',  () => { el.ctxValue.textContent  = el.ctxSlider.value;  state.options.num_ctx = parseInt(el.ctxSlider.value); });
  el.systemPrompt.addEventListener('input', () => { state.systemPrompt = el.systemPrompt.value; });
  el.languageSelect.addEventListener('change', () => {
    state.language = el.languageSelect.value;
    localStorage.setItem('oc-language', state.language);
  });
  el.thinkingChk.addEventListener('change', () => {
    state.thinkingMode = el.thinkingChk.checked;
    if (state.thinkingMode) state.options.think = true; else delete state.options.think;
  });
  el.modelSelect.addEventListener('change', () => {
    state.model = el.modelSelect.value;
    state.pendingFiles = []; clearAttachPreview();
    loadCapabilities(state.model);
  });

  el.btnWebSearch.addEventListener('click', () => {
    state.webSearchEnabled = !state.webSearchEnabled;
    el.btnWebSearch.classList.toggle('active', state.webSearchEnabled);
    el.btnWebSearch.setAttribute('aria-pressed', state.webSearchEnabled);
    el.btnWebSearch.title = state.webSearchEnabled
      ? 'Busca web ativa — clique para desativar'
      : 'Ativar busca web (RAG)';
  });

  el.btnAttach.addEventListener('click', () => el.fileInput.click());
  el.fileInput.addEventListener('change', handleFileSelect);

  el.btnModelInfo.addEventListener('click', showModelInfo);
  el.modalClose.addEventListener('click', closeModal);
  el.modalBackdrop.addEventListener('click', e => { if (e.target === el.modalBackdrop) closeModal(); });

  el.confirmCancel.addEventListener('click', closeConfirm);
  el.confirmBackdrop.addEventListener('click', e => { if (e.target === el.confirmBackdrop) closeConfirm(); });

  el.renameClose.addEventListener('click', closeRename);
  el.renameCancel.addEventListener('click', closeRename);
  el.renameBackdrop.addEventListener('click', e => { if (e.target === el.renameBackdrop) closeRename(); });
  el.renameInput.addEventListener('keydown', e => { if (e.key === 'Enter') el.renameConfirm.click(); });

  el.btnMemory.addEventListener('click', openMemoryModal);
  el.memoryModalClose.addEventListener('click', () => { el.memoryBackdrop.hidden = true; });
  el.memoryBackdrop.addEventListener('click', e => { if (e.target === el.memoryBackdrop) el.memoryBackdrop.hidden = true; });
  el.btnMemoryAdd.addEventListener('click', addMemory);
  el.memoryInput.addEventListener('keydown', e => { if (e.key === 'Enter') addMemory(); });

  el.btnNewProject.addEventListener('click', () => openProjectModal('new'));
  el.projectModalClose.addEventListener('click', closeProjectModal);
  el.projectBackdrop.addEventListener('click', e => { if (e.target === el.projectBackdrop) closeProjectModal(); });
  el.projectFormCancel.addEventListener('click', closeProjectModal);
  el.projectFormSave.addEventListener('click', saveProject);
  el.projectNameInput.addEventListener('keydown', e => { if (e.key === 'Enter') saveProject(); });
  el.btnProjectAddFile.addEventListener('click', () => el.projectFileInput.click());
  el.projectFileInput.addEventListener('change', handleProjectFileUpload);
  el.btnProjectAddText.addEventListener('click', () => { el.projectTextAdd.hidden = false; el.projectTextName.focus(); });
  el.projectTextCancel.addEventListener('click', () => { el.projectTextAdd.hidden = true; });
  el.projectTextSave.addEventListener('click', saveProjectText);
  el.btnProjectNewChat.addEventListener('click', startProjectChat);
  el.btnProjectDelete.addEventListener('click', handleDeleteCurrentProject);
  el.btnClearProject.addEventListener('click', clearProjectContext);

  el.searchInput.addEventListener('input', filterHistory);
  document.querySelectorAll('.suggestion-chip').forEach(btn => {
    btn.addEventListener('click', () => {
      el.messageInput.value = btn.dataset.prompt; updateCharCount(); el.messageInput.focus();
    });
  });
}

/* ════════════════════════════════════════════════════════════════
   STOP STREAMING
════════════════════════════════════════════════════════════════ */
function stopStreaming() {
  if (state.abortController) state.abortController.abort();
  setStreamingUI(false);
}

function setStreamingUI(streaming) {
  state.isStreaming = streaming;
  el.iconSend.style.display = streaming ? 'none' : '';
  el.iconStop.style.display = streaming ? '' : 'none';
  el.btnSend.classList.toggle('stopping', streaming);
}

/* ════════════════════════════════════════════════════════════════
   ANEXOS
════════════════════════════════════════════════════════════════ */
async function handleFileSelect(e) {
  const files = Array.from(e.target.files);
  if (!files.length) return;
  const sv = state.capabilities?.supportsVision ?? false;
  for (const file of files) {
    const ext = file.name.split('.').pop().toLowerCase();
    const isImage = ['jpg','jpeg','png','webp','gif'].includes(ext);
    if (isImage && sv) {
      const b64full = await fileToBase64(file);
      const b64pure = b64full.includes(',') ? b64full.split(',')[1] : b64full;
      state.pendingFiles.push({ name: file.name, type: 'image', data: b64pure });
      addAttachPreview(file.name, 'image', b64full);
    } else if (['pdf','docx','txt','md'].includes(ext)) {
      addAttachPreview(file.name, 'doc', null);
      const text = await extractFileText(file, ext);
      if (text) state.pendingFiles.push({ name: file.name, type: 'doc', data: text });
    } else { alert(`Tipo de arquivo não suportado: .${ext}`); }
  }
  e.target.value = '';
}

function fileToBase64(file) {
  return new Promise((res, rej) => {
    const r = new FileReader(); r.onload = () => res(r.result); r.onerror = rej; r.readAsDataURL(file);
  });
}

async function extractFileText(file, ext) {
  if (ext === 'txt' || ext === 'md') return await file.text();
  const formData = new FormData();
  formData.append('file', file);
  try {
    const res = await fetch(`${API.BASE}/api/files/extract`, { method: 'POST', body: formData });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    return data.text || '';
  } catch (err) {
    console.error('Erro ao extrair texto:', err);
    alert('Não foi possível extrair o texto do arquivo.');
    return null;
  }
}

function addAttachPreview(name, type, src) {
  if (!el.attachPreview) return;
  el.attachPreview.hidden = false;
  const item = document.createElement('div');
  item.className = 'attach-item';
  item.dataset.name = name;
  item.innerHTML = `
    ${type === 'image' && src ? `<img src="${src}" alt="${escapeHtml(name)}" />` : '<span class="attach-icon">📄</span>'}
    <span class="attach-name">${escapeHtml(name)}</span>
    <button class="attach-remove" title="Remover">✕</button>
  `;
  item.querySelector('.attach-remove').addEventListener('click', () => {
    state.pendingFiles = state.pendingFiles.filter(f => f.name !== name);
    item.remove();
    if (!el.attachPreview.querySelector('.attach-item')) el.attachPreview.hidden = true;
  });
  el.attachPreview.appendChild(item);
}

function clearAttachPreview() {
  if (el.attachPreview) { el.attachPreview.innerHTML = ''; el.attachPreview.hidden = true; }
  state.pendingFiles = [];
}

/* ════════════════════════════════════════════════════════════════
   MODELOS
════════════════════════════════════════════════════════════════ */
async function loadModels() {
  try {
    const res = await fetch(`${API.BASE}/api/models`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    const models = data.models || [];
    el.modelSelect.innerHTML = '';
    if (!models.length) {
      el.modelSelect.innerHTML = '<option value="">Nenhum modelo encontrado</option>';
      setStatus(false, 'Nenhum modelo instalado'); return;
    }
    models.forEach(m => {
      const opt = document.createElement('option');
      opt.value = m.name; opt.textContent = m.name;
      el.modelSelect.appendChild(opt);
    });
    state.model = models[0].name;
    setStatus(true, `Ollama online · ${models.length} modelo${models.length > 1 ? 's' : ''}`);
    await loadCapabilities(state.model);
  } catch (err) {
    console.error('Erro ao carregar modelos:', err);
    el.modelSelect.innerHTML = '<option value="">Ollama indisponível</option>';
    setStatus(false, 'Ollama offline');
  }
}

/* ════════════════════════════════════════════════════════════════
   HISTÓRICO
════════════════════════════════════════════════════════════════ */
let allHistory = [];

async function loadHistory() {
  try {
    const res = await fetch(`${API.BASE}/api/history`);
    if (!res.ok) return;
    allHistory = await res.json();
    renderHistory(allHistory);
  } catch (err) { console.error('Erro histórico:', err); }
}

function buildHistoryItem(conv) {
  const item = document.createElement('div');
  item.className = 'history-item' + (conv.id === state.conversationId ? ' active' : '') + (conv.pinned ? ' pinned' : '');
  item.dataset.id = conv.id;
  const dateStr = formatDate(new Date(conv.updatedAt || conv.createdAt));
  const pinIcon = conv.pinned
    ? `<svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><path d="M16 2H8a1 1 0 0 0-.707 1.707L9 5.414V10l-2 2v2h6v5l1 1 1-1v-5h6v-2l-2-2V5.414l1.707-1.707A1 1 0 0 0 16 2z"/></svg>`
    : `<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 2H8a1 1 0 0 0-.707 1.707L9 5.414V10l-2 2v2h6v5l1 1 1-1v-5h6v-2l-2-2V5.414l1.707-1.707A1 1 0 0 0 16 2z"/></svg>`;

  item.innerHTML = `
    <div class="history-item-content">
      <div class="history-item-title" title="${escapeHtml(conv.title)}">
        ${conv.pinned ? '<span class="pin-indicator">📌</span>' : ''}${escapeHtml(conv.title)}
      </div>
      <div class="history-item-meta">
        <span>${dateStr}</span>
        <span class="history-item-model">${escapeHtml(conv.modelName || '')}</span>
      </div>
    </div>
    <div class="history-item-actions">
      <button class="btn-history-action pin ${conv.pinned ? 'pinned' : ''}" title="${conv.pinned ? 'Desafixar' : 'Fixar'}">${pinIcon}</button>
      <button class="btn-history-action rename" title="Renomear">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
          <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
        </svg>
      </button>
      <button class="btn-history-action delete" title="Deletar">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/>
          <path d="M10 11v6M14 11v6"/>
        </svg>
      </button>
    </div>
  `;
  item.querySelector('.pin').addEventListener('click', e => { e.stopPropagation(); togglePin(conv.id); });
  item.querySelector('.rename').addEventListener('click', e => { e.stopPropagation(); openRename(conv.id, conv.title, item); });
  item.querySelector('.delete').addEventListener('click', e => { e.stopPropagation(); deleteConversation(conv.id, item); });
  item.addEventListener('click', () => loadConversation(conv.id));
  return item;
}

function renderHistory(items) {
  el.historyList.innerHTML = '';
  if (!items.length) { el.historyList.appendChild(el.historyEmpty); return; }

  const pinned  = items.filter(c => c.pinned);
  const recents = items.filter(c => !c.pinned);

  if (pinned.length) {
    const sep = document.createElement('div');
    sep.className = 'history-section-sep'; sep.textContent = 'Fixados';
    el.historyList.appendChild(sep);
    pinned.forEach(c => el.historyList.appendChild(buildHistoryItem(c)));
    if (recents.length) {
      const sep2 = document.createElement('div');
      sep2.className = 'history-section-sep'; sep2.textContent = 'Recentes';
      el.historyList.appendChild(sep2);
    }
  }
  recents.forEach(c => el.historyList.appendChild(buildHistoryItem(c)));
}

function filterHistory() {
  const term = el.searchInput.value.toLowerCase().trim();
  renderHistory(term ? allHistory.filter(c => c.title.toLowerCase().includes(term)) : allHistory);
}

async function loadConversation(id) {
  try {
    const res = await fetch(`${API.BASE}/api/history/${id}`);
    if (!res.ok) throw new Error();
    const conv = await res.json();
    state.conversationId = id;
    if (conv.modelName) {
      el.modelSelect.value = conv.modelName;
      state.model = conv.modelName;
      await loadCapabilities(state.model);
    }
    el.messagesArea.innerHTML = '';
    showChat();
    (conv.messages || []).forEach(msg => {
      // Usa o campo thinkingEnabled da mensagem para decidir se mostra pensamento
      const shouldShowThinking = msg.role === 'assistant' && msg.thinkingEnabled === true;
      appendMessage(msg.role, msg.content, false, [], shouldShowThinking);
    });
    scrollToBottom();
    document.querySelectorAll('.history-item').forEach(i => i.classList.remove('active'));
    document.querySelector(`.history-item[data-id="${id}"]`)?.classList.add('active');
  } catch (err) { console.error('Erro ao carregar conversa:', err); }
}

/* ── Renomear ─── */
let _renameTarget = null;
function openRename(id, currentTitle, itemEl) {
  _renameTarget = { id, itemEl };
  el.renameInput.value = currentTitle;
  el.renameBackdrop.hidden = false;
  setTimeout(() => { el.renameInput.focus(); el.renameInput.select(); }, 50);
  el.renameConfirm.onclick = async () => {
    const newTitle = el.renameInput.value.trim();
    if (!newTitle) return;
    try {
      const res = await fetch(`${API.BASE}/api/history/${id}/rename`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: newTitle }),
      });
      if (!res.ok) throw new Error();
      const titleEl = itemEl.querySelector('.history-item-title');
      if (titleEl) { titleEl.textContent = newTitle; titleEl.title = newTitle; }
      const hist = allHistory.find(c => c.id === id);
      if (hist) hist.title = newTitle;
      closeRename();
    } catch { alert('Erro ao renomear conversa.'); }
  };
}
function closeRename() { el.renameBackdrop.hidden = true; _renameTarget = null; }

/* ── Delete conversa ─── */
function deleteConversation(id, itemEl) { openConfirm(() => performDelete(id, itemEl)); }
async function performDelete(id, itemEl) {
  try {
    const res = await fetch(`${API.BASE}/api/history/${id}`, { method: 'DELETE' });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`Erro ${res.status}: ${text || 'Falha ao deletar'}`);
    }
    allHistory = allHistory.filter(c => c.id !== id);
    itemEl.remove();
    document.querySelectorAll('.history-section-sep').forEach(sep => {
      const next = sep.nextElementSibling;
      if (!next || next.classList.contains('history-section-sep')) sep.remove();
    });
    if (!allHistory.length) el.historyList.appendChild(el.historyEmpty);
    if (state.conversationId === id) newConversation();
  } catch (err) { 
    console.error('Erro ao deletar conversa:', err);
    alert(`Falha ao deletar conversa: ${err.message}`);
  }
}

let _confirmCallback = null;
function openConfirm(cb) {
  _confirmCallback = cb;
  el.confirmBackdrop.hidden = false;
  el.confirmDelete.onclick = () => { const cb = _confirmCallback; closeConfirm(); if (cb) cb(); };
}
function closeConfirm() { el.confirmBackdrop.hidden = true; _confirmCallback = null; }

/* ════════════════════════════════════════════════════════════════
   NOVA CONVERSA
════════════════════════════════════════════════════════════════ */
function newConversation() {
  state.conversationId = null;
  el.messagesArea.innerHTML = '';
  el.messageInput.value = '';
  updateCharCount();
  clearAttachPreview();
  showWelcome();
  document.querySelectorAll('.history-item').forEach(i => i.classList.remove('active'));
  if (window.innerWidth <= 768) el.sidebar.classList.remove('open');
}

/* ════════════════════════════════════════════════════════════════
   ENVIO + STREAMING
════════════════════════════════════════════════════════════════ */
async function sendMessage() {
  const text = el.messageInput.value.trim();
  if (!text && !state.pendingFiles.length) return;
  if (!state.model) { alert('Selecione um modelo antes de enviar.'); return; }

  const filesToSend = [...state.pendingFiles];
  el.messageInput.value = '';
  el.messageInput.style.height = 'auto';
  updateCharCount();
  clearAttachPreview();
  showChat();

  // Congela o estado do thinking no momento do envio
  const thinkingActiveNow = state.thinkingMode;

  appendMessage('user', text, false, filesToSend);
  const assistantEl = appendMessage('assistant', '', true, [], thinkingActiveNow);
  const textEl   = assistantEl.querySelector('.message-text');
  const cursorEl = assistantEl.querySelector('.streaming-cursor');

  setStreamingUI(true);
  state.abortController = new AbortController();

  const isNew    = !state.conversationId;
  const endpoint = isNew ? `${API.BASE}/api/chat/new` : `${API.BASE}/api/chat`;

  const images   = filesToSend.filter(f => f.type === 'image').map(f => f.data);
  const docTexts = filesToSend.filter(f => f.type === 'doc').map(f => `[Conteúdo de "${f.name}"]:\n${f.data}`).join('\n\n');
  const fullText = docTexts ? (text ? `${text}\n\n${docTexts}` : docTexts) : text;

  let finalSystemPrompt = state.systemPrompt || null;
  if (state.language) {
    const li = `Responda SEMPRE em ${state.language}.`;
    finalSystemPrompt = finalSystemPrompt ? `${li}\n\n${finalSystemPrompt}` : li;
  }

  const body = {
    message: fullText, model: state.model,
    options: state.options, systemPrompt: finalSystemPrompt,
    images, webSearch: state.webSearchEnabled,
    ...(state.activeProjectId && { projectId: state.activeProjectId }),
    ...(!isNew && state.conversationId && { conversationId: state.conversationId }),
  };

  let fullResponse = '', thinkingText = '';
  let searchBanner = null;

  try {
    const response = await fetch(endpoint, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body), signal: state.abortController.signal,
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    const reader  = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '', currentEvent = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop();
      let pendingData = null;

      const flushEvent = (evName, data) => {
        if (evName === 'conversation-id') { state.conversationId = data.trim(); return; }

        if (evName === 'search-start') {
          searchBanner = document.createElement('div');
          searchBanner.className = 'search-status-banner';
          searchBanner.innerHTML = `<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg> Buscando na web...`;
          el.messagesArea.insertBefore(searchBanner, assistantEl);
          el.btnWebSearch.classList.add('searching');
          return;
        }
        if (evName === 'search-done') {
          if (searchBanner) {
            searchBanner.classList.add('done');
            setTimeout(() => searchBanner?.remove(), 600);
            searchBanner = null;
          }
          el.btnWebSearch.classList.remove('searching');
          return;
        }

        if (evName === 'done' || data.trim() === '[DONE]') {
          cursorEl?.remove();
          // Usa thinkingActiveNow para decidir se renderiza o bloco de pensamento
          renderFinal(textEl, thinkingActiveNow ? thinkingText : '', fullResponse);
          scrollToBottom(); return;
        }
        if (evName === 'error') {
          textEl.innerHTML = `<span style="color:var(--danger)">Erro: ${escapeHtml(data.trim())}</span>`;
          cursorEl?.remove(); return;
        }
        if (evName === 'thinking') {
          // Só acumula e renderiza pensamento se thinkingActiveNow era true no envio
          if (thinkingActiveNow) {
            thinkingText += data;
            renderStreaming(textEl, thinkingText, fullResponse);
            scrollToBottom();
          }
          return;
        }
        if (evName === 'token' || evName === '') {
          fullResponse += data;
          // Usa thinkingActiveNow, não state.thinkingMode (que pode ter mudado)
          renderStreaming(textEl, thinkingActiveNow ? thinkingText : '', fullResponse);
          scrollToBottom();
        }
      };

      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('event:')) {
          if (pendingData !== null) { flushEvent(currentEvent, pendingData); pendingData = null; }
          currentEvent = trimmed.slice(6).trim(); continue;
        }
        if (line.startsWith('data:')) {
          const raw = line.slice(5);
          pendingData = pendingData === null ? raw : pendingData + '\n' + raw;
          continue;
        }
        if (trimmed === '') {
          if (pendingData !== null) { flushEvent(currentEvent, pendingData); pendingData = null; }
          currentEvent = '';
        }
      }
      if (pendingData !== null) { flushEvent(currentEvent, pendingData); pendingData = null; }
    }

    if (textEl.querySelector('.streaming-cursor')) {
      cursorEl?.remove();
      renderFinal(textEl, thinkingActiveNow ? thinkingText : '', fullResponse);
    }
  } catch (err) {
    searchBanner?.remove();
    el.btnWebSearch.classList.remove('searching');
    if (err.name === 'AbortError') {
      cursorEl?.remove();
      if (fullResponse) renderFinal(textEl, thinkingActiveNow ? thinkingText : '', fullResponse);
      else textEl.innerHTML = '<em style="color:var(--text-2)">Geração interrompida.</em>';
    } else {
      console.error('Erro SSE:', err);
      textEl.innerHTML = `<span style="color:var(--danger)">Erro: ${escapeHtml(err.message)}</span>`;
      cursorEl?.remove();
    }
  } finally {
    setStreamingUI(false);
    state.abortController = null;
    el.messageInput.focus();
    await loadHistory();
  }
}

/* ════════════════════════════════════════════════════════════════
   RENDERIZAÇÃO
════════════════════════════════════════════════════════════════ */
function parseStoredMessage(raw) {
  if (!raw) return { thinking: '', content: '' };
  const openTag = '<thinking>', closeTag = '</thinking>\n\n';
  const start = raw.indexOf(openTag), end = raw.indexOf(closeTag);
  if (start === 0 && end !== -1) {
    return { thinking: raw.slice(openTag.length, end), content: raw.slice(end + closeTag.length) };
  }
  return { thinking: '', content: raw };
}

// Detecta se o conteúdo salvo possui bloco de pensamento
function hasThinkingBlock(raw) {
  if (!raw) return false;
  return raw.indexOf('<thinking>') === 0 && raw.indexOf('</thinking>') !== -1;
}

function renderStreaming(textEl, thinking, content) {
  let html = '';
  if (thinking) html += `<details class="thinking-block"><summary>Pensamento</summary><p>${escapeHtml(thinking)}</p></details>`;
  const count = (content.match(/```/g) || []).length;
  const safe  = count % 2 !== 0 ? content + '\n```' : content;
  html += renderMarkdown(safe) + '<span class="streaming-cursor"></span>';
  textEl.innerHTML = html;
}

function renderFinal(textEl, thinking, content) {
  let html = '';
  if (thinking) html += `<details class="thinking-block"><summary>Pensamento</summary><p>${escapeHtml(thinking)}</p></details>`;
  html += renderMarkdown(content);
  textEl.innerHTML = html;
  textEl.querySelectorAll('pre code').forEach(b => hljs.highlightElement(b));
}

function appendMessage(role, rawContent, streaming, files = [], thinkingWasActive = null) {
  const msg = document.createElement('div');
  msg.className = `message ${role}`;
  const avatarLabel = role === 'user' ? 'Eu' : '⬡';
  const roleLabel   = role === 'user' ? 'Você' : 'Assistente';

  const { thinking, content } = parseStoredMessage(rawContent || '');

  // Só exibe bloco de pensamento se thinkingWasActive === true
  const shouldShowThinking = thinking && (thinkingWasActive === true);
  const thinkHtml = shouldShowThinking
    ? `<details class="thinking-block"><summary>Pensamento</summary><p>${escapeHtml(thinking)}</p></details>`
    : '';

  const rendered = content ? renderMarkdown(content) : '';

  const docFiles = files.filter(f => f.type === 'doc');
  const imgFiles = files.filter(f => f.type === 'image');
  const attachHtml = [
    ...docFiles.map(f => `<div class="message-attach-card"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg><span class="message-attach-name">${escapeHtml(f.name)}</span></div>`),
    ...imgFiles.map(f => `<img src="data:image/jpeg;base64,${f.data}" class="message-img-thumb" alt="${escapeHtml(f.name)}" />`),
  ].join('');
  const attachSection = attachHtml ? `<div class="message-attachments">${attachHtml}</div>` : '';

  msg.innerHTML = `
    <div class="message-inner">
      <div class="message-avatar">${avatarLabel}</div>
      <div class="message-content">
        <div class="message-role">${roleLabel}</div>
        ${attachSection}
        <div class="message-text">${thinkHtml}${rendered}${streaming ? '<span class="streaming-cursor"></span>' : ''}</div>
      </div>
    </div>
  `;
  if (!streaming && content) msg.querySelectorAll('pre code').forEach(b => hljs.highlightElement(b));
  el.messagesArea.appendChild(msg);
  scrollToBottom();
  return msg;
}

function renderMarkdown(text) {
  try { return marked.parse(text || ''); } catch { return escapeHtml(text || ''); }
}

/* ════════════════════════════════════════════════════════════════
   INFO DO MODELO
════════════════════════════════════════════════════════════════ */
async function showModelInfo() {
  if (!state.model) return;
  el.modalTitle.textContent = state.model;
  el.modalBody.innerHTML = '<div class="spinner"></div>';
  el.modalBackdrop.hidden = false;
  try {
    const res  = await fetch(`${API.BASE}/api/models/${encodeURIComponent(state.model)}/info`);
    if (!res.ok) throw new Error();
    const data = await res.json();
    const caps = state.capabilities;
    const rows = [
      ['Modelo',      data.modelfile?.split('\n')[0] || state.model],
      ['Parâmetros',  data.details?.parameter_size || '—'],
      ['Quantização', data.details?.quantization_level || '—'],
      ['Família',     data.details?.family || '—'],
      ['Contexto',    caps?.contextLength > 0 ? caps.contextLength.toLocaleString() + ' tokens' : '—'],
      ['Thinking',    caps?.supportsThinking ? '✅ Sim' : '❌ Não'],
      ['Vision',      caps?.supportsVision   ? '✅ Sim' : '❌ Não'],
      ['Tamanho',     data.size ? formatBytes(data.size) : '—'],
    ];
    el.modalBody.innerHTML = `<table class="model-info-table">${rows.map(([k,v]) => `<tr><td>${escapeHtml(k)}</td><td>${escapeHtml(String(v))}</td></tr>`).join('')}</table>`;
  } catch { el.modalBody.innerHTML = '<span style="color:var(--text-2)">Informações não disponíveis.</span>'; }
}

/* ════════════════════════════════════════════════════════════════
   PIN
════════════════════════════════════════════════════════════════ */
async function togglePin(id) {
  try {
    const res  = await fetch(`${API.BASE}/api/history/${id}/pin`, { method: 'PATCH' });
    const data = await res.json();
    if (!data.success && data.reason === 'limit_reached') {
      alert('Máximo de 3 chats fixados atingido.'); return;
    }
    await loadHistory();
  } catch (err) { console.error('Erro ao fixar conversa:', err); }
}

/* ════════════════════════════════════════════════════════════════
   PROJETOS
════════════════════════════════════════════════════════════════ */
let currentProjectId = null;

async function loadProjects() {
  try {
    const res = await fetch(`${API.BASE}/api/projects`);
    if (!res.ok) return;
    renderProjects(await res.json());
  } catch (err) { console.error('Erro ao carregar projetos:', err); }
}

function renderProjects(projects) {
  el.projectsList.innerHTML = '';
  if (!projects.length) { el.projectsList.appendChild(el.projectsEmpty); return; }
  projects.forEach(proj => {
    const item = document.createElement('div');
    item.className = 'project-item' + (proj.id === state.activeProjectId ? ' active' : '');
    item.dataset.id = proj.id;
    item.innerHTML = `
      <span class="project-item-icon">📁</span>
      <div class="project-item-content">
        <div class="project-item-name" title="${escapeHtml(proj.name)}">${escapeHtml(proj.name)}</div>
        <div class="project-item-count">${proj.files ? proj.files.length + ' arquivo(s)' : 'Sem arquivos'}</div>
      </div>
      <div class="history-item-actions">
        <button class="btn-history-action rename" title="Editar">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
        </button>
        <button class="btn-history-action delete" title="Deletar">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/>
            <path d="M10 11v6M14 11v6"/>
          </svg>
        </button>
      </div>
    `;
    item.querySelector('.rename').addEventListener('click', e => { e.stopPropagation(); openProjectModal('edit', proj.id); });
    item.querySelector('.delete').addEventListener('click', e => { e.stopPropagation(); deleteProject(proj.id, item); });
    item.addEventListener('click', () => openProjectModal('detail', proj.id));
    el.projectsList.appendChild(item);
  });
}

async function deleteProject(id, itemEl) {
  openConfirm(async () => {
    try {
      await fetch(`${API.BASE}/api/projects/${id}`, { method: 'DELETE' });
      itemEl.remove();
      if (state.activeProjectId === id) clearProjectContext();
      await loadProjects();
    } catch (err) { console.error('Erro ao deletar projeto:', err); }
  });
}

async function handleDeleteCurrentProject() {
  if (!currentProjectId) return;
  openConfirm(async () => {
    try {
      await fetch(`${API.BASE}/api/projects/${currentProjectId}`, { method: 'DELETE' });
      if (state.activeProjectId === currentProjectId) clearProjectContext();
      closeProjectModal();
      await loadProjects();
    } catch (err) { console.error('Erro ao deletar projeto:', err); }
  });
}

/* ── Modal de Projeto ─── */
async function openProjectModal(mode, projectId) {
  currentProjectId = projectId || null;
  el.projectFormSection.hidden   = true;
  el.projectDetailSection.hidden = true;
  el.projectTextAdd.hidden       = true;
  el.projectBackdrop.hidden      = false;

  if (mode === 'new') {
    el.projectModalTitle.textContent = 'Novo Projeto';
    el.projectNameInput.value = ''; el.projectDescInput.value = '';
    el.projectFormSection.hidden = false;
    setTimeout(() => el.projectNameInput.focus(), 50);

  } else if (mode === 'edit') {
    el.projectModalTitle.textContent = 'Editar Projeto';
    try {
      const res = await fetch(`${API.BASE}/api/projects/${projectId}`);
      const proj = await res.json();
      el.projectNameInput.value = proj.name || '';
      el.projectDescInput.value = proj.description || '';
      el.projectFormSection.hidden = false;
      setTimeout(() => el.projectNameInput.focus(), 50);
    } catch { el.projectFormSection.hidden = false; }

  } else if (mode === 'detail') {
    try {
      const res = await fetch(`${API.BASE}/api/projects/${projectId}`);
      const proj = await res.json();
      el.projectModalTitle.textContent = proj.name;
      el.projectDetailDesc.textContent = proj.description || 'Sem descrição.';
      el.projectDetailSection.hidden   = false;
      renderProjectFiles(proj.files || []);
      await loadProjectChats(projectId);
    } catch { el.projectDetailSection.hidden = false; }
  }
}

function closeProjectModal() {
  el.projectBackdrop.hidden = true;
  currentProjectId = null;
  el.projectTextAdd.hidden = true;
}

async function saveProject() {
  const name = el.projectNameInput.value.trim();
  if (!name) { el.projectNameInput.focus(); return; }
  const desc = el.projectDescInput.value.trim();
  try {
    if (currentProjectId) {
      await fetch(`${API.BASE}/api/projects/${currentProjectId}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, description: desc }),
      });
    } else {
      const res = await fetch(`${API.BASE}/api/projects`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, description: desc }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    }
    closeProjectModal();
    await loadProjects();
  } catch (err) {
    console.error('Erro ao salvar projeto:', err);
    alert('Erro ao salvar projeto. Verifique o console.');
  }
}

/* ── Chats do projeto ─── */
async function loadProjectChats(projectId) {
  if (!el.projectChatsList) return;
  el.projectChatsList.innerHTML = '<div class="spinner"></div>';
  try {
    const res = await fetch(`${API.BASE}/api/history/project/${projectId}`);
    if (!res.ok) throw new Error();
    const chats = await res.json();
    renderProjectChats(chats);
  } catch {
    el.projectChatsList.innerHTML = '<p class="project-chats-empty">Erro ao carregar chats.</p>';
  }
}

function renderProjectChats(chats) {
  el.projectChatsList.innerHTML = '';
  if (!chats.length) {
    el.projectChatsList.innerHTML = '<p class="project-chats-empty">Nenhum chat iniciado com este projeto ainda.</p>';
    return;
  }
  chats.forEach(conv => {
    const item = document.createElement('div');
    item.className = 'project-chat-item';
    const dateStr = formatDate(new Date(conv.updatedAt || conv.createdAt));
    item.innerHTML = `
      <div class="project-chat-item-info">
        <div class="project-chat-item-title" title="${escapeHtml(conv.title)}">${escapeHtml(conv.title)}</div>
        <div class="project-chat-item-meta">
          <span>${dateStr}</span>
          <span class="project-chat-item-model">${escapeHtml(conv.modelName || '')}</span>
        </div>
      </div>
      <svg class="project-chat-item-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="9 18 15 12 9 6"/>
      </svg>
    `;
    item.addEventListener('click', () => {
      closeProjectModal();
      loadConversation(conv.id);
    });
    el.projectChatsList.appendChild(item);
  });
}

/* ── Arquivos do projeto ─── */
function renderProjectFiles(files) {
  el.projectFilesList.innerHTML = '';
  if (!files.length) {
    el.projectFilesList.innerHTML = '<p style="font-size:12px;color:var(--text-2);text-align:center;padding:12px 0">Nenhum arquivo ainda.</p>';
    return;
  }
  files.forEach(f => {
    const item = document.createElement('div');
    item.className = 'project-file-item';
    const icon = f.fileType === 'pdf' ? '📄' : f.fileType === 'docx' ? '📝' : f.fileType === 'text' ? '✏️' : '📃';
    const sizeKb = f.contentLength ? Math.round(f.contentLength / 1024) : 0;
    const size = sizeKb > 0 ? sizeKb + ' KB' : '';
    item.innerHTML = `
      <span class="project-file-icon">${icon}</span>
      <span class="project-file-name" title="${escapeHtml(f.filename)}">${escapeHtml(f.filename)}</span>
      <span class="project-file-size">${size}</span>
      <button class="btn-project-file-delete" onclick="deleteProjectFile('${f.id}', this)" title="Remover">✕</button>
    `;
    el.projectFilesList.appendChild(item);
  });
}

async function handleProjectFileUpload(e) {
  const files = Array.from(e.target.files);
  if (!files.length || !currentProjectId) return;
  for (const file of files) {
    const formData = new FormData(); formData.append('file', file);
    try {
      await fetch(`${API.BASE}/api/projects/${currentProjectId}/files`, { method: 'POST', body: formData });
    } catch (err) { console.error('Erro ao enviar arquivo:', err); }
  }
  e.target.value = '';
  const res = await fetch(`${API.BASE}/api/projects/${currentProjectId}`);
  const proj = await res.json();
  renderProjectFiles(proj.files || []);
  await loadProjects();
}

async function saveProjectText() {
  const name    = el.projectTextName.value.trim() || 'Texto';
  const content = el.projectTextContent.value.trim();
  if (!content || !currentProjectId) return;
  try {
    await fetch(`${API.BASE}/api/projects/${currentProjectId}/texts`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, content }),
    });
    el.projectTextName.value = ''; el.projectTextContent.value = '';
    el.projectTextAdd.hidden = true;
    const res = await fetch(`${API.BASE}/api/projects/${currentProjectId}`);
    const proj = await res.json();
    renderProjectFiles(proj.files || []);
    await loadProjects();
  } catch (err) { console.error('Erro ao salvar texto:', err); }
}

window.deleteProjectFile = async function(fileId, btn) {
  try {
    await fetch(`${API.BASE}/api/projects/files/${fileId}`, { method: 'DELETE' });
    btn.closest('.project-file-item').remove();
    await loadProjects();
  } catch (err) { console.error('Erro ao deletar arquivo:', err); }
};

function startProjectChat() {
  if (!currentProjectId) return;
  const name = el.projectModalTitle.textContent;
  setProjectContext(currentProjectId, name);
  closeProjectModal();
  newConversation();
  setTimeout(() => el.messageInput.focus(), 100);
}

function setProjectContext(id, name) {
  state.activeProjectId    = id;
  state.activeProjectName  = name;
  el.projectContextBadge.hidden = false;
  el.projectContextName.textContent = `Projeto: ${name}`;
  document.querySelectorAll('.project-item').forEach(i => i.classList.toggle('active', i.dataset.id === id));
}

function clearProjectContext() {
  state.activeProjectId = null; state.activeProjectName = '';
  el.projectContextBadge.hidden = true;
  document.querySelectorAll('.project-item').forEach(i => i.classList.remove('active'));
}

function closeModal() { el.modalBackdrop.hidden = true; }

/* ════════════════════════════════════════════════════════════════
   MEMÓRIA
════════════════════════════════════════════════════════════════ */
async function openMemoryModal() {
  el.memoryBackdrop.hidden = false;
  await renderMemoryList();
}

async function renderMemoryList() {
  el.memoryList.innerHTML = '<div class="spinner"></div>';
  try {
    const res = await fetch(`${API.BASE}/api/memories`);
    if (!res.ok) throw new Error();
    const memories = await res.json();
    if (!memories.length) { el.memoryList.innerHTML = '<p class="memory-empty">Nenhuma memória ainda.</p>'; return; }
    el.memoryList.innerHTML = memories.map(m => `
      <div class="memory-item ${m.active ? '' : 'inactive'}" data-id="${m.id}">
        <div class="memory-item-content">
          <span class="memory-category-tag">${escapeHtml(m.category || 'geral')}</span>
          <span class="memory-text">${escapeHtml(m.content)}</span>
        </div>
        <div class="memory-item-actions">
          <button class="btn-memory-toggle" onclick="toggleMemory('${m.id}')" title="${m.active ? 'Desativar' : 'Ativar'}">${m.active ? '●' : '○'}</button>
          <button class="btn-memory-delete" onclick="deleteMemory('${m.id}')" title="Deletar">✕</button>
        </div>
      </div>
    `).join('');
  } catch { el.memoryList.innerHTML = '<span style="color:var(--danger)">Erro ao carregar memórias.</span>'; }
}

async function addMemory() {
  const content = el.memoryInput.value.trim();
  if (!content) return;
  try {
    await fetch(`${API.BASE}/api/memories`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content, category: el.memoryCategory.value }),
    });
    el.memoryInput.value = '';
    await renderMemoryList();
  } catch { console.error('Erro ao adicionar memória'); }
}

window.toggleMemory = async (id) => { try { await fetch(`${API.BASE}/api/memories/${id}/toggle`, { method: 'PATCH' }); await renderMemoryList(); } catch {} };
window.deleteMemory = async (id) => { try { await fetch(`${API.BASE}/api/memories/${id}`, { method: 'DELETE' }); await renderMemoryList(); } catch {} };

/* ════════════════════════════════════════════════════════════════
   HELPERS
════════════════════════════════════════════════════════════════ */
function showWelcome() { el.welcomeScreen.classList.remove('hidden'); el.messagesArea.classList.add('hidden'); }
function showChat()    { el.welcomeScreen.classList.add('hidden');    el.messagesArea.classList.remove('hidden'); }
function scrollToBottom() { el.messagesArea.scrollTop = el.messagesArea.scrollHeight; }
function updateCharCount() { const l = el.messageInput.value.length; el.charCount.textContent = `${l} / 32000`; el.charCount.style.color = l > 30000 ? 'var(--danger)' : ''; }
function setStatus(online, text) { el.statusDot.className = 'status-dot ' + (online ? 'online' : 'offline'); el.statusText.textContent = text; }

window.copyCode = btn => {
  navigator.clipboard.writeText(btn.closest('pre').querySelector('code').textContent).then(() => {
    btn.textContent = '✓ Copiado'; btn.classList.add('copied');
    setTimeout(() => { btn.textContent = 'Copiar'; btn.classList.remove('copied'); }, 2000);
  });
};

function escapeHtml(s) { return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
function formatDate(d) {
  const diff = Date.now() - d, days = Math.floor(diff / 86400000);
  if (days === 0) return d.toLocaleTimeString('pt-BR', { hour:'2-digit', minute:'2-digit' });
  if (days === 1) return 'Ontem';
  if (days < 7)  return d.toLocaleDateString('pt-BR', { weekday:'short' });
  return d.toLocaleDateString('pt-BR', { day:'2-digit', month:'2-digit' });
}
function formatBytes(b) {
  if (b < 1024) return b + ' B';
  if (b < 1024**2) return (b/1024).toFixed(1)+' KB';
  if (b < 1024**3) return (b/1024**2).toFixed(1)+' MB';
  return (b/1024**3).toFixed(2)+' GB';
}

document.addEventListener('DOMContentLoaded', init);
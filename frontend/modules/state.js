/* ════════════════════════════════════════════════════════════════
   ESTADO GLOBAL & CONFIGURAÇÃO
════════════════════════════════════════════════════════════════ */

const API = { BASE: 'http://localhost:8080' };

const state = {
  conversationId: null,
  isStreaming: false,
  model: '',
  options: {},
  systemPrompt: '',
  language: '',
  thinkingMode: false,
  webSearchEnabled: false,
  codeModeEnabled: false,
  capabilities: null,
  pendingFiles: [],
  abortController: null,
  activeProjectId: null,
  activeProjectName: '',
  activeGithubRepo: null,
  codeSession: null,
  inlinePreviews: {},
  nextInlinePreviewId: 0,
  fsRootPath: null,
};

const $ = id => document.getElementById(id);
let el = {};

/* ════════════════════════════════════════════════════════════════
   INICIALIZAÇÃO DE REFERÊNCIAS DOM
════════════════════════════════════════════════════════════════ */

function initRefs() {
  el = {
    sidebar: $('sidebar'),
    historyList: $('history-list'),
    historyEmpty: $('history-empty'),
    searchInput: $('search-input'),
    modelSelect: $('model-select'),
    modelTags: $('model-tags'),
    messagesArea: $('messages-area'),
    welcomeScreen: $('welcome-screen'),
    messageInput: $('message-input'),
    btnSend: $('btn-send'),
    iconSend: $('icon-send'),
    iconStop: $('icon-stop'),
    btnNewChat: $('btn-new-chat'),
    btnSettings: $('btn-settings'),
    btnTheme: $('btn-theme'),
    btnMemory: $('btn-memory'),
    btnSidebarCollapse: $('btn-sidebar-collapse'),
    collapseIcon: $('collapse-icon'),
    settingsPanel: $('settings-panel'),
    btnModelInfo: $('btn-model-info'),
    charCount: $('char-count'),
    statusDot: $('status-dot'),
    statusText: $('status-text'),
    btnToggle: $('btn-toggle-sidebar'),
    tempSlider: $('param-temperature'),
    tempValue: $('temperature-value'),
    ctxSlider: $('param-ctx'),
    ctxValue: $('ctx-value'),
    systemPrompt: $('param-system'),
    languageSelect: $('param-language'),
    thinkingChk: $('param-thinking'),
    thinkingField: $('thinking-field'),
    modalBackdrop: $('modal-backdrop'),
    modalTitle: $('modal-title'),
    modalBody: $('modal-body'),
    modalClose: $('modal-close'),
    confirmBackdrop: $('confirm-backdrop'),
    confirmDelete: $('confirm-delete'),
    confirmCancel: $('confirm-cancel'),
    renameBackdrop: $('rename-backdrop'),
    renameInput: $('rename-input'),
    renameConfirm: $('rename-confirm'),
    renameCancel: $('rename-cancel'),
    renameClose: $('rename-close'),
    memoryBackdrop: $('memory-backdrop'),
    memoryModalClose: $('memory-modal-close'),
    memoryInput: $('memory-input'),
    memoryCategory: $('memory-category'),
    btnMemoryAdd: $('btn-memory-add'),
    memoryList: $('memory-list'),
    accentSwatches: $('accent-swatches'),
    customColorInput: $('custom-color-input'),
    projectsList: $('projects-list'),
    projectsEmpty: $('projects-empty'),
    btnNewProject: $('btn-new-project'),
    projectBackdrop: $('project-backdrop'),
    projectModalTitle: $('project-modal-title'),
    projectModalClose: $('project-modal-close'),
    projectModalBody: $('project-modal-body'),
    projectFormSection: $('project-form-section'),
    projectDetailSection: $('project-detail-section'),
    projectNameInput: $('project-name-input'),
    projectDescInput: $('project-desc-input'),
    projectFormCancel: $('project-form-cancel'),
    projectFormSave: $('project-form-save'),
    projectDetailDesc: $('project-detail-desc'),
    projectFilesList: $('project-files-list'),
    btnProjectAddFile: $('btn-project-add-file'),
    btnProjectAddText: $('btn-project-add-text'),
    projectFileInput: $('project-file-input'),
    projectTextAdd: $('project-text-add'),
    projectTextName: $('project-text-name'),
    projectTextContent: $('project-text-content'),
    projectTextCancel: $('project-text-cancel'),
    projectTextSave: $('project-text-save'),
    btnProjectNewChat: $('btn-project-new-chat'),
    btnProjectDelete: $('btn-project-delete'),
    projectChatsSection: $('project-chats-section'),
    projectChatsList: $('project-chats-list'),
    projectContextBadge: $('project-context-badge'),
    projectContextName: $('project-context-name'),
    btnClearProject: $('btn-clear-project'),
    btnAttach: $('btn-attach'),
    btnWebSearch: $('btn-web-search'),
    btnCodeMode: $('btn-code-mode'),
    btnGithub: $('btn-github'),
    fileInput: $('file-input'),
    attachPreview: $('attach-preview'),
    codePanel: $('code-panel'),
    codePanelClose: $('code-panel-close'),
    codePanelDownloadZip: $('code-panel-download-zip'),
    codeFileTree: $('code-file-tree'),
    codeEditorContent: $('code-editor-content'),
    codeEditorFilename: $('code-editor-filename'),
    codePanelDownloadFile: $('code-panel-download-file'),
    codeDiffToggle: $('code-diff-toggle'),
    githubBackdrop: $('github-backdrop'),
    githubModalClose: $('github-modal-close'),
    githubRepoInput: $('github-repo-input'),
    githubBranchInput: $('github-branch-input'),
    githubTokenInput: $('github-token-input'),
    githubPrivateChk: $('github-private-chk'),
    btnGithubConnect: $('btn-github-connect'),
    githubRepoList: $('github-repo-list'),
    githubContextBadge: $('github-context-badge'),
    githubContextName: $('github-context-name'),
    btnClearGithub: $('btn-clear-github'),
    codingOverlay: $('coding-overlay'),
    codingOverlayFiles: $('coding-overlay-files'),
    codePanelTabs: $('code-panel-tabs'),
    codePanelPreview: $('code-panel-preview'),
    codePanelResizeHandle: $('code-panel-resize-handle'),
    // i18n
    interfaceLangSelect: $('param-ui-language'),
  };
}

/* ════════════════════════════════════════════════════════════════
   INICIALIZAÇÃO GLOBAL
════════════════════════════════════════════════════════════════ */

async function init() {
  // i18n deve ser inicializado ANTES de qualquer referência DOM
  initI18n();
  initRefs();
  await setupAllModules();
}

let allHistory = [];
let currentProjectId = null;
let githubRepos = [];
let currentFileId = null;
let showingDiff = false;
let activeTab = 'code';
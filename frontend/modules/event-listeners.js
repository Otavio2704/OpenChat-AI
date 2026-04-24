/* ════════════════════════════════════════════════════════════════
   EVENT LISTENERS - Configuração de todos os listeners
════════════════════════════════════════════════════════════════ */

function setupEventListeners() {
  setupSidebarCollapse();

  el.btnSend.addEventListener('click', () => {
    if (state.isStreaming) stopStreaming();
    else sendMessage();
  });
  el.messageInput.addEventListener('keydown', e => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!state.isStreaming) sendMessage();
    }
  });
  el.messageInput.addEventListener('input', () => {
    el.messageInput.style.height = 'auto';
    el.messageInput.style.height = Math.min(el.messageInput.scrollHeight, 200) + 'px';
    updateCharCount();
  });

  el.btnNewChat.addEventListener('click', newConversation);
  el.btnToggle.addEventListener('click', () => el.sidebar.classList.toggle('open'));
  el.messagesArea.addEventListener('click', () => {
    if (window.innerWidth <= 768) el.sidebar.classList.remove('open');
  });

  el.btnSettings.addEventListener('click', () => {
    el.settingsPanel.hidden = !el.settingsPanel.hidden;
    el.btnSettings.classList.toggle('active', !el.settingsPanel.hidden);
  });
  el.btnTheme.addEventListener('click', () =>
    applyTheme(document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark')
  );

  el.accentSwatches.addEventListener('click', e => {
    const s = e.target.closest('.swatch');
    if (s && s.dataset.color) applyAccent(s.dataset.color);
  });
  el.customColorInput.addEventListener('input', e => applyAccent(e.target.value));

  el.tempSlider.addEventListener('input', () => {
    el.tempValue.textContent = el.tempSlider.value;
    state.options.temperature = parseFloat(el.tempSlider.value);
  });
  el.ctxSlider.addEventListener('input', () => {
    el.ctxValue.textContent = el.ctxSlider.value;
    state.options.num_ctx = parseInt(el.ctxSlider.value);
  });
  el.systemPrompt.addEventListener('input', () => {
    state.systemPrompt = el.systemPrompt.value;
  });
  el.languageSelect.addEventListener('change', () => {
    state.language = el.languageSelect.value;
    localStorage.setItem('oc-language', state.language);
  });
  el.thinkingChk.addEventListener('change', () => {
    state.thinkingMode = el.thinkingChk.checked;
    if (state.thinkingMode) state.options.think = true;
    else delete state.options.think;
  });
  el.modelSelect.addEventListener('change', () => {
    state.model = el.modelSelect.value;
    state.pendingFiles = [];
    clearAttachPreview();
    loadCapabilities(state.model);
  });

  el.btnWebSearch.addEventListener('click', () => {
    state.webSearchEnabled = !state.webSearchEnabled;
    el.btnWebSearch.classList.toggle('active', state.webSearchEnabled);
    el.btnWebSearch.setAttribute('aria-pressed', state.webSearchEnabled);
    el.btnWebSearch.title = state.webSearchEnabled ? t('input.webSearchActive') : t('input.webSearch');
  });

  el.btnCodeMode.addEventListener('click', () => {
    state.codeModeEnabled = !state.codeModeEnabled;
    el.btnCodeMode.classList.toggle('active', state.codeModeEnabled);
    el.btnCodeMode.title = state.codeModeEnabled ? t('input.codeModeActive') : t('input.codeMode');
    if (state.codeModeEnabled && state.conversationId) loadCodeSession();
  });

  el.btnGithub.addEventListener('click', openGithubModal);

  el.codePanelClose.addEventListener('click', closeCodePanel);
  el.codePanelDownloadZip.addEventListener('click', downloadProjectZip);
  el.codePanelDownloadFile.addEventListener('click', downloadCurrentFile);
  el.codeDiffToggle.addEventListener('click', () => {
    if (typeof globalThis.toggleDiffView === 'function') globalThis.toggleDiffView();
  });

  el.githubModalClose.addEventListener('click', closeGithubModal);
  el.githubBackdrop.addEventListener('click', e => {
    if (e.target === el.githubBackdrop) closeGithubModal();
  });
  el.btnGithubConnect.addEventListener('click', connectGithubRepo);
  el.btnClearGithub.addEventListener('click', clearGithubContext);

  el.btnAttach.addEventListener('click', () => el.fileInput.click());
  el.fileInput.addEventListener('change', handleFileSelect);

  el.btnModelInfo.addEventListener('click', showModelInfo);
  el.modalClose.addEventListener('click', closeModal);
  el.modalBackdrop.addEventListener('click', e => {
    if (e.target === el.modalBackdrop) closeModal();
  });

  el.confirmCancel.addEventListener('click', closeConfirm);
  el.confirmBackdrop.addEventListener('click', e => {
    if (e.target === el.confirmBackdrop) closeConfirm();
  });

  el.renameClose.addEventListener('click', closeRename);
  el.renameCancel.addEventListener('click', closeRename);
  el.renameBackdrop.addEventListener('click', e => {
    if (e.target === el.renameBackdrop) closeRename();
  });
  el.renameInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') el.renameConfirm.click();
  });

  el.btnMemory.addEventListener('click', openMemoryModal);
  el.memoryModalClose.addEventListener('click', () => {
    el.memoryBackdrop.hidden = true;
  });
  el.memoryBackdrop.addEventListener('click', e => {
    if (e.target === el.memoryBackdrop) el.memoryBackdrop.hidden = true;
  });
  el.btnMemoryAdd.addEventListener('click', addMemory);
  el.memoryInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') addMemory();
  });

  el.btnNewProject.addEventListener('click', () => openProjectModal('new'));
  el.projectModalClose.addEventListener('click', closeProjectModal);
  el.projectBackdrop.addEventListener('click', e => {
    if (e.target === el.projectBackdrop) closeProjectModal();
  });
  el.projectFormCancel.addEventListener('click', closeProjectModal);
  el.projectFormSave.addEventListener('click', () => {
    if (typeof globalThis.saveProject === 'function') globalThis.saveProject();
  });
  el.projectNameInput.addEventListener('keydown', e => {
    if (e.key === 'Enter' && typeof globalThis.saveProject === 'function') globalThis.saveProject();
  });
  el.btnProjectAddFile.addEventListener('click', () => el.projectFileInput.click());
  el.projectFileInput.addEventListener('change', handleProjectFileUpload);
  el.btnProjectAddText.addEventListener('click', () => {
    el.projectTextAdd.hidden = false;
    el.projectTextName.focus();
  });
  el.projectTextCancel.addEventListener('click', () => {
    el.projectTextAdd.hidden = true;
  });
  el.projectTextSave.addEventListener('click', () => {
    if (typeof globalThis.saveProjectText === 'function') globalThis.saveProjectText();
  });
  el.btnProjectNewChat.addEventListener('click', () => {
    if (typeof globalThis.startProjectChat === 'function') globalThis.startProjectChat();
  });

  // ─── FIX: usa a referência globalThis que é garantida pelo projects.js ───
  el.btnProjectDelete.addEventListener('click', () => {
    if (typeof globalThis.handleDeleteCurrentProject === 'function') {
      globalThis.handleDeleteCurrentProject();
    } else {
      console.error('handleDeleteCurrentProject não encontrada no globalThis');
    }
  });

  el.btnClearProject.addEventListener('click', clearProjectContext);

  el.searchInput.addEventListener('input', filterHistory);
  document.querySelectorAll('.suggestion-chip').forEach(btn => {
    btn.addEventListener('click', () => {
      el.messageInput.value = btn.dataset.prompt;
      updateCharCount();
      el.messageInput.focus();
    });
  });
}

async function setupAllModules() {
  setupEventListeners();
  setupCodePanelResize();
  configureMarked();
  loadPreferences();
  setupSectionToggles();
  // Inicializa o seletor de idioma da interface e aplica textos traduzidos
  setupInterfaceLanguageSelector();
  applyStaticTranslations();
  applyDynamicTranslations();
  await Promise.all([loadModels(), loadHistory(), loadProjects()]);
}
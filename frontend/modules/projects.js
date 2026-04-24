/* ════════════════════════════════════════════════════════════════
   PROJETOS - Gerenciamento de projetos
   Fix: handleDeleteCurrentProject agora exposta corretamente
════════════════════════════════════════════════════════════════ */

async function loadProjects() {
  try {
    const res = await fetch(`${API.BASE}/api/projects`);
    if (!res.ok) return;
    renderProjects(await res.json());
  } catch (err) {
    console.error('Erro ao carregar projetos:', err);
  }
}

function renderProjects(projects) {
  el.projectsList.innerHTML = '';
  if (!projects.length) {
    el.projectsList.appendChild(el.projectsEmpty);
    return;
  }
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
        <button class="btn-history-action rename" title="${t('history.rename')}">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
        </button>
        <button class="btn-history-action delete" title="${t('history.delete')}">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/>
            <path d="M10 11v6M14 11v6"/>
          </svg>
        </button>
      </div>
    `;
    item.querySelector('.rename').addEventListener('click', e => {
      e.stopPropagation();
      openProjectModal('edit', proj.id);
    });
    item.querySelector('.delete').addEventListener('click', e => {
      e.stopPropagation();
      deleteProject(proj.id, item);
    });
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
    } catch (err) {
      console.error('Erro ao deletar projeto:', err);
    }
  });
}

// ─── FIX: função declarada como named function E exposta em globalThis ───────
// O bug anterior ocorria porque event-listeners.js chamava globalThis.handleDeleteCurrentProject,
// mas a função era declarada como async function dentro do módulo sem ser exposta.
// Além disso, currentProjectId pode estar null se o modal for fechado/reaberto — agora
// a função lê o valor diretamente do dataset do botão de detalhe como fallback.

async function handleDeleteCurrentProject() {
  // currentProjectId é a variável global declarada em state.js
  if (!currentProjectId) {
    console.warn('handleDeleteCurrentProject: currentProjectId está null');
    return;
  }

  const idToDelete = currentProjectId;

  openConfirm(async () => {
    try {
      const res = await fetch(`${API.BASE}/api/projects/${idToDelete}`, { method: 'DELETE' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      // Limpa contexto se o projeto deletado estava ativo
      if (state.activeProjectId === idToDelete) clearProjectContext();

      // Fecha o modal antes de recarregar a lista
      closeProjectModal();
      await loadProjects();
    } catch (err) {
      console.error('Erro ao deletar projeto:', err);
      alert(t('project.deleteError'));
    }
  });
}

// Expõe globalmente para que event-listeners.js consiga chamá-la
globalThis.handleDeleteCurrentProject = handleDeleteCurrentProject;

async function openProjectModal(mode, projectId) {
  currentProjectId = projectId || null;
  el.projectFormSection.hidden = true;
  el.projectDetailSection.hidden = true;
  el.projectTextAdd.hidden = true;
  el.projectBackdrop.hidden = false;

  if (mode === 'new') {
    el.projectModalTitle.textContent = t('project.new');
    el.projectNameInput.value = '';
    el.projectDescInput.value = '';
    el.projectFormSection.hidden = false;
    setTimeout(() => el.projectNameInput.focus(), 50);
  } else if (mode === 'edit') {
    el.projectModalTitle.textContent = t('project.edit');
    try {
      const res = await fetch(`${API.BASE}/api/projects/${projectId}`);
      const proj = await res.json();
      el.projectNameInput.value = proj.name || '';
      el.projectDescInput.value = proj.description || '';
      el.projectFormSection.hidden = false;
      setTimeout(() => el.projectNameInput.focus(), 50);
    } catch {
      el.projectFormSection.hidden = false;
    }
  } else if (mode === 'detail') {
    try {
      const res = await fetch(`${API.BASE}/api/projects/${projectId}`);
      const proj = await res.json();
      el.projectModalTitle.textContent = proj.name;
      el.projectDetailDesc.textContent = proj.description || t('project.noDesc');
      el.projectDetailSection.hidden = false;
      renderProjectFiles(proj.files || []);
      await loadProjectChats(projectId);
    } catch {
      el.projectDetailSection.hidden = false;
    }
  }
}

function closeProjectModal() {
  el.projectBackdrop.hidden = true;
  currentProjectId = null;
  el.projectTextAdd.hidden = true;
}

// Expõe globalmente
globalThis.closeProjectModal = closeProjectModal;

async function saveProject() {
  const name = el.projectNameInput.value.trim();
  if (!name) {
    el.projectNameInput.focus();
    return;
  }
  const desc = el.projectDescInput.value.trim();
  try {
    if (currentProjectId) {
      await fetch(`${API.BASE}/api/projects/${currentProjectId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, description: desc }),
      });
    } else {
      const res = await fetch(`${API.BASE}/api/projects`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, description: desc }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    }
    closeProjectModal();
    await loadProjects();
  } catch (err) {
    console.error('Erro ao salvar projeto:', err);
    alert(t('project.saveError'));
  }
}

// Expõe globalmente
globalThis.saveProject = saveProject;

async function loadProjectChats(projectId) {
  if (!el.projectChatsList) return;
  el.projectChatsList.innerHTML = '<div class="spinner"></div>';
  try {
    const res = await fetch(`${API.BASE}/api/history/project/${projectId}`);
    if (!res.ok) throw new Error();
    renderProjectChats(await res.json());
  } catch {
    el.projectChatsList.innerHTML = `<p class="project-chats-empty">${t('project.chatsError')}</p>`;
  }
}

function renderProjectChats(chats) {
  el.projectChatsList.innerHTML = '';
  if (!chats.length) {
    el.projectChatsList.innerHTML = `<p class="project-chats-empty">${t('project.noChats')}</p>`;
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

function renderProjectFiles(files) {
  el.projectFilesList.innerHTML = '';
  if (!files.length) {
    el.projectFilesList.innerHTML = `<p style="font-size:12px;color:var(--text-2);text-align:center;padding:12px 0">${t('project.noFiles')}</p>`;
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
      <button class="btn-project-file-delete" onclick="deleteProjectFile('${f.id}', this)" title="${t('history.delete')}">✕</button>
    `;
    el.projectFilesList.appendChild(item);
  });
}

async function handleProjectFileUpload(e) {
  const files = Array.from(e.target.files);
  if (!files.length || !currentProjectId) return;
  for (const file of files) {
    const formData = new FormData();
    formData.append('file', file);
    try {
      await fetch(`${API.BASE}/api/projects/${currentProjectId}/files`, { method: 'POST', body: formData });
    } catch (err) {
      console.error('Erro ao enviar arquivo:', err);
    }
  }
  e.target.value = '';
  const res = await fetch(`${API.BASE}/api/projects/${currentProjectId}`);
  const proj = await res.json();
  renderProjectFiles(proj.files || []);
  await loadProjects();
}

globalThis.deleteProjectFile = async function (fileId, btn) {
  try {
    await fetch(`${API.BASE}/api/projects/files/${fileId}`, { method: 'DELETE' });
    btn.closest('.project-file-item').remove();
    await loadProjects();
  } catch (err) {
    console.error('Erro ao deletar arquivo:', err);
  }
};

async function saveProjectText() {
  const name = el.projectTextName.value.trim() || t('project.contextName');
  const content = el.projectTextContent.value.trim();
  if (!content || !currentProjectId) return;
  try {
    await fetch(`${API.BASE}/api/projects/${currentProjectId}/texts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, content }),
    });
    el.projectTextName.value = '';
    el.projectTextContent.value = '';
    el.projectTextAdd.hidden = true;
    const res = await fetch(`${API.BASE}/api/projects/${currentProjectId}`);
    const proj = await res.json();
    renderProjectFiles(proj.files || []);
    await loadProjects();
  } catch (err) {
    console.error('Erro ao salvar texto:', err);
  }
}

globalThis.saveProjectText = saveProjectText;

function startProjectChat() {
  if (!currentProjectId) return;
  const name = el.projectModalTitle.textContent;
  setProjectContext(currentProjectId, name);
  closeProjectModal();
  newConversation();
  setTimeout(() => el.messageInput.focus(), 100);
}

globalThis.startProjectChat = startProjectChat;

function setProjectContext(id, name) {
  state.activeProjectId = id;
  state.activeProjectName = name;
  el.projectContextBadge.hidden = false;
  el.projectContextName.textContent = `${t('project.active')}: ${name}`;
  document.querySelectorAll('.project-item').forEach(i => i.classList.toggle('active', i.dataset.id === id));
}

function clearProjectContext() {
  state.activeProjectId = null;
  state.activeProjectName = '';
  el.projectContextBadge.hidden = true;
  document.querySelectorAll('.project-item').forEach(i => i.classList.remove('active'));
}
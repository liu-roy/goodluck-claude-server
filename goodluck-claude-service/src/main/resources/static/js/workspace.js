/* global CodeMirror */
(function () {
  'use strict';

  const $ = id => document.getElementById(id);
  const selProject = $('selProject'), selSession = $('selSession'), selBranch = $('selBranch');
  const branchGroup = $('branchGroup');
  const chatPanel = $('chatPanel'), chatMessages = $('chatMessages'), chatEmpty = $('chatEmpty');
  const txtPrompt = $('txtPrompt'), btnSend = $('btnSend'), sendStatus = $('sendStatus'), inputWrap = $('inputWrap');
  const treePlaceholder = $('treePlaceholder'), treeRoot = $('treeRoot');
  const treeSearchWrap = $('treeSearchWrap'), treeSearchInput = $('treeSearchInput'), searchResult = $('searchResult');
  const treePanel = $('treePanel');
  const filePath = $('filePath'), ctActions = $('ctActions'), contentBody = $('contentBody');
  const tabChat = $('tabChat'), tabHistory = $('tabHistory');
  const viewChat = $('viewChat'), viewHistory = $('viewHistory');
  const historyList = $('historyList'), historyEmpty = $('historyEmpty');

  let curFile = null, curContent = '', editMode = false;
  let isSending = false;
  let allFiles = [];
  /** @type {CodeMirror.Editor | null} */
  let codeMirrorInstance = null;

  const authCheck = fetch('/projects/list', { method: 'GET', credentials: 'include' })
    .then(r => {
      if (r.status === 401) { location.href = '/login.html'; return null; }
      return r;
    })
    .catch(() => { location.href = '/login.html'; return null; });

  function destroyCodeEditor() {
    if (codeMirrorInstance) {
      const w = codeMirrorInstance.getWrapperElement();
      if (w && w.parentNode) w.parentNode.removeChild(w);
      codeMirrorInstance = null;
    }
  }

  function resizeCodeEditor() {
    if (!codeMirrorInstance) return;
    const host = contentBody.querySelector('.code-editor-host');
    if (!host) return;
    const h = Math.max(Math.round(host.getBoundingClientRect().height), 240);
    codeMirrorInstance.setSize('100%', h);
    codeMirrorInstance.refresh();
  }

  function cmModeFromPath(filePathStr) {
    const base = filePathStr.includes('/') ? filePathStr.slice(filePathStr.lastIndexOf('/') + 1) : filePathStr;
    const dot = base.lastIndexOf('.');
    const ext = dot > 0 ? base.slice(dot + 1).toLowerCase() : '';
    const map = {
      java: 'text/x-java',
      js: 'javascript',
      jsx: 'javascript',
      mjs: 'javascript',
      cjs: 'javascript',
      ts: { name: 'javascript', typescript: true },
      tsx: { name: 'javascript', typescript: true },
      json: { name: 'javascript', json: true },
      html: 'htmlmixed',
      htm: 'htmlmixed',
      vue: 'htmlmixed',
      xml: 'text/xml',
      xsd: 'text/xml',
      svg: 'text/xml',
      css: 'css',
      scss: 'css',
      less: 'css',
      md: 'markdown',
      yml: 'yaml',
      yaml: 'yaml',
      sh: 'shell',
      bash: 'shell',
      sql: 'sql',
      py: 'python',
      properties: 'properties',
      gradle: 'text/x-groovy',
      groovy: 'text/x-groovy',
      go: 'text/x-go',
      rs: 'rust',
      c: 'text/x-csrc',
      h: 'text/x-csrc',
      cpp: 'text/x-c++src',
      hpp: 'text/x-c++src',
      cs: 'text/x-csharp',
      rb: 'ruby',
      php: 'php',
      kt: 'text/x-java',
      kts: 'text/x-java',
    };
    if (map[ext]) return map[ext];
    const lower = base.toLowerCase();
    if (lower === 'dockerfile') return 'dockerfile';
    if (lower === 'makefile') return 'shell';
    return null;
  }

  function mountCodeEditor() {
    destroyCodeEditor();
    contentBody.innerHTML = '';
    const host = document.createElement('div');
    host.className = 'code-editor-host';
    contentBody.appendChild(host);

    const mode = curFile ? cmModeFromPath(curFile) : null;
    codeMirrorInstance = CodeMirror(host, {
      value: curContent,
      lineNumbers: true,
      mode: mode || null,
      theme: 'dracula',
      readOnly: !editMode,
      indentUnit: 4,
      tabSize: 4,
      lineWrapping: true,
      autofocus: editMode,
    });

    requestAnimationFrame(() => {
      resizeCodeEditor();
      if (editMode) codeMirrorInstance.focus();
    });
  }

  window.addEventListener('resize', () => {
    resizeCodeEditor();
  });
  if (typeof ResizeObserver !== 'undefined') {
    new ResizeObserver(() => resizeCodeEditor()).observe(contentBody);
  }

  async function api(method, path, body) {
    const authRes = await authCheck;
    if (authRes === null) return;
    const opts = { method, credentials: 'include', headers: { 'Content-Type': 'application/json' } };
    if (body !== undefined) opts.body = JSON.stringify(body);
    const r = await fetch(path, opts);
    if (r.status === 401) { location.href = '/login.html'; return; }
    const d = await r.json();
    if (d.code !== undefined && d.code !== 200 && d.code !== 0) throw new Error(d.message || '请求失败');
    return d;
  }
  function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }
  async function logout() {
    try {
      await fetch('/user/logout', { method: 'POST', credentials: 'include' });
    } finally {
      localStorage.clear();
      location.href = '/login.html';
    }
  }

  function switchTab(tab) {
    const isChat = tab === 'chat';
    tabChat.classList.toggle('active', isChat);
    tabHistory.classList.toggle('active', !isChat);
    viewChat.classList.toggle('hidden', !isChat);
    viewHistory.classList.toggle('hidden', isChat);
    if (!isChat) renderHistory();
  }
  tabChat.onclick = () => switchTab('chat');
  tabHistory.onclick = () => switchTab('history');

  function renderMd(text) {
    if (!text) return '';
    let s = esc(text);
    s = s.replace(/```([\s\S]*?)```/g, (_, code) => `<pre>${code}</pre>`);
    s = s.replace(/`([^`]+)`/g, '<code>$1</code>');
    return s;
  }

  function createBubble(role, content, time) {
    const isUser = role === 'user';
    const row = document.createElement('div');
    row.className = 'bubble-row ' + (isUser ? 'user' : 'assistant');
    const avatar = document.createElement('div');
    avatar.className = 'bubble-avatar';
    avatar.textContent = isUser ? '我' : 'C';
    const body = document.createElement('div');
    body.className = 'bubble-body';
    const meta = document.createElement('div');
    meta.className = 'bubble-meta';
    meta.innerHTML = `<span class="name">${isUser ? '我' : 'Claude'}</span>` + (time ? `<span>${esc(time)}</span>` : '');
    const bubble = document.createElement('div');
    bubble.className = 'bubble-content';
    bubble.innerHTML = renderMd(content || '');
    body.appendChild(meta);
    body.appendChild(bubble);
    row.appendChild(avatar);
    row.appendChild(body);
    return row;
  }
  function showTypingIndicator() {
    const row = document.createElement('div');
    row.className = 'bubble-row assistant'; row.id = 'typingRow';
    const avatar = document.createElement('div');
    avatar.className = 'bubble-avatar'; avatar.textContent = 'C';
    const body = document.createElement('div');
    body.className = 'bubble-body';
    body.innerHTML = '<div class="bubble-content"><div class="typing-indicator"><span></span><span></span><span></span></div></div>';
    row.appendChild(avatar); row.appendChild(body);
    chatMessages.appendChild(row);
    chatMessages.scrollTop = chatMessages.scrollHeight;
  }
  function hideTypingIndicator() { const t = $('typingRow'); if (t) t.remove(); }
  function scrollToBottom() { requestAnimationFrame(() => { chatMessages.scrollTop = chatMessages.scrollHeight; }); }

  async function loadProjects() {
    try {
      const d = await api('GET', '/projects/list');
      selProject.innerHTML = '<option value="">-- 选择项目 --</option>' +
        ((d.data || []).map(p => `<option value="${esc(p)}">${esc(p)}</option>`).join(''));
    } catch (e) { selProject.innerHTML = '<option value="">加载失败</option>'; }
  }

  async function loadSessions(projectName) {
    try {
      const d = await api('GET', '/sessions');
      const all = d && Array.isArray(d.data) ? d.data : [];
      const pn = projectName != null ? projectName : selProject.value;
      const list = pn ? all.filter(s => (s && s.projectName) === pn) : all;

      const cur = selSession.value;
      const baseOption = pn ? '-- 选择会话（当前项目） --' : '-- 选择会话 --';
      selSession.innerHTML = `<option value="">${baseOption}</option>` +
        (list.map(s => {
          const label = (s.projectName ? s.projectName + ' · ' : '') +
            (s.sessionId || '').slice(0, 8) +
            (s.lastUsedAt ? ' · ' + s.lastUsedAt : '');
          return `<option value="${esc(s.sessionId)}">${esc(label)}</option>`;
        }).join(''));
      if (pn && !list.length) {
        selSession.innerHTML += `<option value="" disabled>该项目暂无会话，请点「新建会话」</option>`;
      }

      if (cur && list.some(s => s && s.sessionId === cur)) selSession.value = cur;
      else selSession.value = '';
    } catch (e) {
      selSession.innerHTML = '<option value="">加载失败</option>';
    }
  }
  $('btnNewSession').onclick = async () => {
    try {
      const pid = selProject.value || null;
      const d = await api('POST', '/sessions', pid ? { projectName: pid } : {});
      await loadSessions(pid || '');
      if (d.data && d.data.sessionId) selSession.value = d.data.sessionId;
      loadMessages(); switchTab('chat');
    } catch (e) { alert('新建会话失败: ' + e.message); }
  };

  async function loadBranches(pid) {
    branchGroup.style.display = pid ? 'flex' : 'none';
    if (!pid) return;
    selBranch.innerHTML = '<option value="">加载中…</option>';
    try {
      const [bl, bc] = await Promise.all([
        api('GET', `/projects/${encodeURIComponent(pid)}/branches`),
        api('GET', `/projects/${encodeURIComponent(pid)}/branch/current`)
      ]);
      const list = Array.isArray(bl.data) ? bl.data : (Array.isArray(bl) ? bl : []);
      const cur = bc && (bc.data != null) ? bc.data : null;
      selBranch.innerHTML = list.length
        ? list.map(b => `<option value="${esc(b)}"${b === cur ? ' selected' : ''}>${esc(b)}</option>`).join('')
        : '<option value="">无远程分支（需已配置 remote 且可 fetch）</option>';
    } catch (e) {
      selBranch.innerHTML = '<option value="">加载失败，点刷新重试</option>';
      console.warn('加载分支失败', e);
    }
  }
  $('btnRefreshBranches').onclick = () => { const p = selProject.value; if (p) loadBranches(p); };
  selBranch.onchange = async () => {
    const pid = selProject.value, br = selBranch.value;
    if (!pid || !br) return;
    try {
      await api('POST', `/projects/${encodeURIComponent(pid)}/branch/checkout`, { branchName: br });
      await loadTree(pid);
    } catch (e) { alert('切换分支失败: ' + e.message); }
  };

  const SVG_CHEVRON = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>';
  const SVG_FOLDER = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round" aria-hidden="true"><path d="M4 7.5A1.5 1.5 0 0 1 5.5 6h4.09a1.5 1.5 0 0 1 1.17.57l1.31 1.64H19a1 1 0 0 1 1 1V17a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7.5z"/></svg>';
  const SVG_FOLDER_OPEN = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round" aria-hidden="true"><path d="M4 8.5V17a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-6.5a1 1 0 0 0-1-1h-7.09a1 1 0 0 1-.76-.35L8.79 5.59A1 1 0 0 0 8.09 5H5a1 1 0 0 0-1 1v2.5z"/><path d="M4 10h16"/></svg>';

  function fileIconSpec(name) {
    const ext = (name.lastIndexOf('.') > 0) ? name.slice(name.lastIndexOf('.') + 1).toLowerCase() : '';
    const byExt = {
      java: { cls: 'java', abbr: 'Ja' },
      xml: { cls: 'xml', abbr: 'XML' },
      yml: { cls: 'yml', abbr: 'Yml' },
      yaml: { cls: 'yml', abbr: 'Yml' },
      json: { cls: 'json', abbr: '{}' },
      md: { cls: 'md', abbr: 'Md' },
      html: { cls: 'html', abbr: 'Htm' },
      htm: { cls: 'html', abbr: 'Htm' },
      css: { cls: 'css', abbr: 'CSS' },
      js: { cls: 'js', abbr: 'JS' },
      ts: { cls: 'ts', abbr: 'TS' },
      jsx: { cls: 'js', abbr: 'JX' },
      tsx: { cls: 'ts', abbr: 'TX' },
      sh: { cls: 'sh', abbr: 'Sh' },
      bat: { cls: 'sh', abbr: 'Bt' },
      sql: { cls: 'sql', abbr: 'SQL' },
      properties: { cls: 'properties', abbr: 'Pr' },
      gradle: { cls: 'gradle', abbr: 'Gr' },
      py: { cls: 'py', abbr: 'Py' },
      go: { cls: 'go', abbr: 'Go' },
      rs: { cls: 'rs', abbr: 'Rs' },
      txt: { cls: 'txt', abbr: 'Txt' },
    };
    if (byExt[ext]) return byExt[ext];
    const lower = name.toLowerCase();
    if (lower === 'dockerfile') return { cls: 'docker', abbr: 'DK' };
    if (lower === 'makefile') return { cls: 'make', abbr: 'Mk' };
    if (name === '.gitignore' || name === '.editorconfig') return { cls: 'properties', abbr: '·' };
    if (ext) {
      const a = ext.length <= 3 ? ext.toUpperCase() : ext.slice(0, 3).toUpperCase();
      return { cls: 'file', abbr: a };
    }
    return { cls: 'file', abbr: '∎' };
  }

  let treeData = [];
  async function loadTree(pid) {
    treePlaceholder.style.display = 'none';
    treeRoot.style.display = 'block';
    treeRoot.innerHTML = '<div class="placeholder">加载中…</div>';
    treeSearchWrap.style.display = 'block';
    treeSearchInput.value = '';
    searchResult.style.display = 'none';
    treeRoot.style.display = 'block';
    try {
      const d = await api('GET', `/projects/${encodeURIComponent(pid)}/tree`);
      treeData = d.data || [];
      allFiles = [];
      flattenFiles(treeData, '');
      treeRoot.innerHTML = '';
      renderTree(treeData, treeRoot, 0, '');
      revealPathInTree(curFile);
    } catch (e) { treeRoot.innerHTML = '<div class="placeholder error">' + esc(e.message) + '</div>'; }
  }

  function revealPathInTree(filePathStr) {
    if (!filePathStr || !treeRoot) return;
    const segments = filePathStr.split('/');
    segments.pop();
    let acc = '';
    for (let i = 0; i < segments.length; i++) {
      acc = acc ? acc + '/' + segments[i] : segments[i];
      const row = treeRoot.querySelector('.t-row[data-dir-path="' + CSS.escape(acc) + '"]');
      if (!row) continue;
      const nodeEl = row.closest('.t-node');
      const ch = nodeEl && nodeEl.querySelector(':scope > .t-children');
      const ar = row.querySelector('.t-arrow');
      const iconSlot = row.querySelector('.t-dir-icon');
      if (ch && ch.classList.contains('collapsed')) {
        ch.classList.remove('collapsed');
        if (ar) ar.classList.remove('collapsed');
        if (iconSlot) iconSlot.innerHTML = SVG_FOLDER_OPEN;
      }
    }
  }

  function flattenFiles(nodes, prefix) {
    nodes.forEach(n => {
      if (n.dir) {
        flattenFiles(n.children || [], prefix ? prefix + '/' + n.name : n.name);
      } else {
        allFiles.push({ name: n.name, path: n.path || (prefix ? prefix + '/' + n.name : n.name) });
      }
    });
  }

  function renderTree(nodes, parent, depth, parentPath) {
    nodes.forEach(n => {
      const node = document.createElement('div');
      node.className = 't-node';
      const row = document.createElement('div');
      row.className = 't-row';
      const segmentPath = parentPath ? parentPath + '/' + n.name : n.name;

      if (n.dir) row.dataset.dirPath = segmentPath;

      if (n.path && n.path === curFile) row.classList.add('active');

      const indent = document.createElement('span');
      indent.className = 't-indent';
      indent.style.width = (depth * 16) + 'px';
      row.appendChild(indent);

      if (n.dir) {
        const hasKids = n.children && n.children.length;
        const arrow = document.createElement('span');
        arrow.className = 't-arrow' + (hasKids ? '' : ' empty');
        if (hasKids) arrow.innerHTML = SVG_CHEVRON;

        const iconWrap = document.createElement('span');
        iconWrap.className = 't-dir-icon';
        iconWrap.innerHTML = SVG_FOLDER;

        const name = document.createElement('span');
        name.className = 't-name';
        name.textContent = n.name;

        row.appendChild(arrow);
        row.appendChild(iconWrap);
        row.appendChild(name);
        node.appendChild(row);

        if (hasKids) {
          const children = document.createElement('div');
          children.className = 't-children collapsed';
          arrow.classList.add('collapsed');
          renderTree(n.children, children, depth + 1, segmentPath);
          node.appendChild(children);
          row.onclick = () => {
            const nowHidden = children.classList.toggle('collapsed');
            arrow.classList.toggle('collapsed', nowHidden);
            iconWrap.innerHTML = nowHidden ? SVG_FOLDER : SVG_FOLDER_OPEN;
          };
        }
      } else {
        const arrow = document.createElement('span');
        arrow.className = 't-arrow empty';
        row.appendChild(arrow);

        const spec = fileIconSpec(n.name);
        const badge = document.createElement('span');
        badge.className = 't-file-badge t-file-badge-' + spec.cls;
        badge.textContent = spec.abbr;

        const name = document.createElement('span');
        name.className = 't-name';
        name.textContent = n.name;

        row.appendChild(badge);
        row.appendChild(name);
        node.appendChild(row);

        if (n.path) row.dataset.filePath = n.path;

        row.onclick = () => {
          document.querySelectorAll('.t-row.active').forEach(r => r.classList.remove('active'));
          row.classList.add('active');
          openFile(n.path);
        };
      }
      parent.appendChild(node);
    });
  }

  function highlightTreeFile(filePathStr) {
    if (!filePathStr || !treeRoot) return;
    document.querySelectorAll('#treeRoot .t-row.active').forEach(r => r.classList.remove('active'));
    const row = treeRoot.querySelector('.t-row[data-file-path="' + CSS.escape(filePathStr) + '"]');
    if (row) row.classList.add('active');
  }

  treeSearchInput.addEventListener('input', () => {
    const q = treeSearchInput.value.trim().toLowerCase();
    if (!q) {
      searchResult.style.display = 'none';
      treeRoot.style.display = 'block';
      return;
    }
    treeRoot.style.display = 'none';
    searchResult.style.display = 'block';
    searchResult.innerHTML = '';

    const matches = allFiles.filter(f => f.name.toLowerCase().includes(q) || f.path.toLowerCase().includes(q));
    if (!matches.length) {
      searchResult.innerHTML = '<div class="sr-empty">未找到匹配文件</div>';
      return;
    }
    matches.slice(0, 50).forEach(f => {
      const item = document.createElement('div');
      item.className = 'sr-item';
      const highlighted = f.name.replace(new RegExp('(' + q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'gi'), '<mark>$1</mark>');
      const dir = f.path.includes('/') ? f.path.slice(0, f.path.lastIndexOf('/')) : '';
      const spec = fileIconSpec(f.name);
      item.innerHTML = `<span class="t-file-badge t-file-badge-${spec.cls}">${esc(spec.abbr)}</span><span class="sr-name">${highlighted}</span>` +
        (dir ? `<span class="sr-path">${esc(dir)}</span>` : '');
      item.onclick = () => {
        openFile(f.path);
        treeSearchInput.value = '';
        searchResult.style.display = 'none';
        treeRoot.style.display = 'block';
        document.querySelectorAll('.t-row.active').forEach(r => r.classList.remove('active'));
      };
      searchResult.appendChild(item);
    });
  });

  async function openFile(path) {
    const pid = selProject.value; if (!pid) return;
    curFile = path; editMode = false;
    filePath.textContent = path;
    destroyCodeEditor();
    contentBody.innerHTML = '<span class="hint">加载中…</span>';
    updateCtActions();
    try {
      const d = await api('GET', `/projects/files/getContent?projectId=${encodeURIComponent(pid)}&filePath=${encodeURIComponent(path)}`);
      curContent = typeof d.data === 'string' ? d.data : '';
      showContent();
      revealPathInTree(path);
      highlightTreeFile(path);
    } catch (e) {
      destroyCodeEditor();
      contentBody.innerHTML = '<span class="error">' + esc(e.message) + '</span>';
    }
  }

  function showContent() {
    if (!curFile) return;
    mountCodeEditor();
    updateCtActions();
  }

  function updateCtActions() {
    ctActions.innerHTML = '';
    if (!curFile) return;
    if (editMode) {
      const saveBtn = document.createElement('button');
      saveBtn.className = 'ct-btn primary'; saveBtn.textContent = '保存';
      saveBtn.onclick = saveFile;
      const cancelBtn = document.createElement('button');
      cancelBtn.className = 'ct-btn'; cancelBtn.textContent = '取消';
      cancelBtn.onclick = () => { editMode = false; showContent(); };
      ctActions.appendChild(saveBtn);
      ctActions.appendChild(cancelBtn);
    } else {
      const editBtn = document.createElement('button');
      editBtn.className = 'ct-btn'; editBtn.textContent = '编辑';
      editBtn.onclick = () => { editMode = true; showContent(); };
      ctActions.appendChild(editBtn);
    }
  }

  async function saveFile() {
    const pid = selProject.value; if (!pid || !curFile) return;
    const text = codeMirrorInstance ? codeMirrorInstance.getValue() : '';
    try {
      await api('PUT', '/projects/files/content', { projectId: pid, filePath: curFile, content: text });
      curContent = text;
      editMode = false; showContent();
    } catch (e) { alert('保存失败: ' + e.message); }
  }

  async function loadMessages() {
    const sid = selSession.value, pid = selProject.value;
    chatMessages.querySelectorAll('.bubble-row').forEach(e => e.remove());
    chatEmpty.style.display = 'flex';
    if (!sid || !pid) { chatEmpty.querySelector('.text').innerHTML = '选择项目和会话后<br>在这里与 Claude 对话'; return; }
    try {
      const d = await api('GET', `/sessions/messages?sessionId=${encodeURIComponent(sid)}&projectName=${encodeURIComponent(pid)}`);
      const list = d.data || [];
      if (!list.length) { chatEmpty.querySelector('.text').innerHTML = '暂无对话记录<br>输入需求开始对话'; return; }
      chatEmpty.style.display = 'none';
      list.forEach(m => { chatMessages.appendChild(createBubble(m.role, m.content, m.time)); });
      scrollToBottom();
    } catch (e) { chatEmpty.querySelector('.text').innerHTML = '加载对话失败'; }
  }

  btnSend.onclick = sendMessage;
  async function sendMessage() {
    if (isSending) { sendStatus.textContent = '正在处理中，请等待当前请求完成…'; sendStatus.className = 'chat-status err'; return; }
    const pid = selProject.value, sid = selSession.value || undefined, prompt = (txtPrompt.value || '').trim();
    if (!pid) { sendStatus.textContent = '请先选择项目'; sendStatus.className = 'chat-status err'; return; }
    if (!prompt) { sendStatus.textContent = '请输入需求'; sendStatus.className = 'chat-status err'; return; }

    isSending = true;
    btnSend.disabled = true;
    inputWrap.classList.add('disabled');
    txtPrompt.disabled = true;
    switchTab('chat');
    chatEmpty.style.display = 'none';
    chatMessages.appendChild(createBubble('user', prompt, new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })));
    scrollToBottom();
    txtPrompt.value = ''; autoResizeTextarea();
    sendStatus.textContent = ''; sendStatus.className = 'chat-status';
    showTypingIndicator();

    try {
      const d = await api('POST', '/projects/generate', { projectName: pid, prompt, sessionId: sid || null });
      hideTypingIndicator();
      const data = d.data || {};
      if (data.sessionId && !selSession.value) { await loadSessions(); selSession.value = data.sessionId; }
      if (data.status === 'SUCCESS') {
        if (data.assistantMessage) {
          chatMessages.appendChild(createBubble('assistant', data.assistantMessage, new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })));
        }
        sendStatus.className = 'chat-status ok'; sendStatus.textContent = '生成完成';
        setTimeout(() => { if (sendStatus.textContent === '生成完成') sendStatus.textContent = ''; }, 3000);
        loadSessions(); loadTree(pid);
      } else {
        const errMsg = data.error || data.assistantMessage || '请求失败';
        chatMessages.appendChild(createBubble('assistant', errMsg, new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })));
        sendStatus.className = 'chat-status err'; sendStatus.textContent = '执行失败';
      }
      scrollToBottom();
    } catch (e) {
      hideTypingIndicator();
      sendStatus.textContent = '失败: ' + e.message; sendStatus.className = 'chat-status err';
    }

    isSending = false;
    btnSend.disabled = false;
    inputWrap.classList.remove('disabled');
    txtPrompt.disabled = false;
    txtPrompt.focus();
  }

  function autoResizeTextarea() {
    txtPrompt.style.height = 'auto';
    txtPrompt.style.height = Math.min(txtPrompt.scrollHeight, 120) + 'px';
  }
  txtPrompt.addEventListener('input', autoResizeTextarea);
  let _isComposing = false;
  let _lastCompositionEndAt = 0;
  txtPrompt.addEventListener('compositionstart', () => { _isComposing = true; });
  txtPrompt.addEventListener('compositionend', () => {
    _isComposing = false;
    _lastCompositionEndAt = Date.now();
  });
  txtPrompt.addEventListener('keydown', e => {
    if (e.key !== 'Enter') return;
    const justEndedComposition = _lastCompositionEndAt && (Date.now() - _lastCompositionEndAt <= 80);
    if (_isComposing || e.isComposing || justEndedComposition) return;
    if (e.shiftKey) return;
    e.preventDefault();
    sendMessage();
  });

  selProject.onchange = () => {
    const pid = selProject.value;
    curFile = null; editMode = false;
    filePath.textContent = '未选择文件';
    destroyCodeEditor();
    contentBody.innerHTML = '<span class="hint">点击左侧文件查看内容</span>';
    ctActions.innerHTML = '';
    sendStatus.textContent = '';
    chatPanel.classList.toggle('visible', !!pid);
    if (!pid) {
      treePlaceholder.style.display = 'block'; treeRoot.style.display = 'none';
      treeSearchWrap.style.display = 'none';
      branchGroup.style.display = 'none';
      loadSessions('');
      return;
    }
    loadSessions(pid);
    loadTree(pid); loadBranches(pid); loadMessages();
    switchTab('chat');
  };
  selSession.onchange = () => { loadMessages(); switchTab('chat'); };

  async function renderHistory() {
    historyList.innerHTML = '';
    historyEmpty.style.display = 'none';
    historyList.appendChild(historyEmpty);
    try {
      const d = await api('GET', '/sessions');
      const list = d.data || [];
      if (!list.length) { historyEmpty.style.display = 'block'; return; }
      list.forEach(s => {
        const card = document.createElement('div');
        card.className = 'session-card';
        card.innerHTML =
          `<div class="s-icon">💬</div>` +
          `<div class="s-info">` +
          `<div class="s-id">${esc(s.projectName || '未绑定项目')} · ${esc((s.sessionId || '').slice(0, 8))}…</div>` +
          `<div class="s-detail"><span>创建: ${esc(s.createdAt || '—')}</span><span>最近: ${esc(s.lastUsedAt || '—')}</span></div>` +
          `</div>` +
          `<button class="s-btn">继续</button>`;
        card.querySelector('.s-btn').onclick = e => { e.stopPropagation(); resumeSession(s); };
        card.onclick = () => resumeSession(s);
        historyList.appendChild(card);
      });
    } catch (e) { historyEmpty.textContent = '加载失败'; historyEmpty.style.display = 'block'; }
  }
  function resumeSession(s) {
    if (s.projectName && selProject.value !== s.projectName) {
      selProject.value = s.projectName;
      selProject.dispatchEvent(new Event('change'));
    }
    selSession.value = s.sessionId || '';
    loadMessages(); switchTab('chat');
    setTimeout(() => txtPrompt.focus(), 100);
  }
  $('btnRefreshHistory').onclick = () => { renderHistory(); loadSessions(); };

  const cloneOverlay = $('cloneOverlay'), cloneStatus = $('cloneStatus'), btnCloneOk = $('btnCloneOk');
  function openCloneModal() { cloneOverlay.classList.add('open'); cloneStatus.textContent = ''; cloneStatus.className = 'clone-status'; }
  function closeCloneModal() { cloneOverlay.classList.remove('open'); }
  $('btnCloneProject').onclick = openCloneModal;
  $('btnCloneClose').onclick = closeCloneModal;
  $('btnCloneCancel').onclick = closeCloneModal;
  cloneOverlay.onclick = e => { if (e.target === cloneOverlay) closeCloneModal(); };
  btnCloneOk.onclick = async () => {
    const gitUrl = $('cloneGitUrl').value.trim();
    if (!gitUrl) { cloneStatus.textContent = '请输入 Git 仓库地址'; cloneStatus.className = 'clone-status err'; return; }
    const body = { gitUrl };
    const branch = $('cloneBranch').value.trim(), projectName = $('cloneProjectName').value.trim();
    const gitUsername = $('cloneUsername').value.trim(), gitPassword = $('clonePassword').value;
    if (branch) body.branch = branch;
    if (projectName) body.projectName = projectName;
    if (gitUsername) body.gitUsername = gitUsername;
    if (gitPassword) body.gitPassword = gitPassword;
    btnCloneOk.disabled = true;
    cloneStatus.textContent = '克隆中，请稍候…'; cloneStatus.className = 'clone-status';
    try {
      const d = await api('POST', '/projects/clone', body);
      cloneStatus.textContent = '克隆成功!'; cloneStatus.className = 'clone-status ok';
      await loadProjects();
      const name = d.data?.projectName || projectName;
      if (name) { selProject.value = name; selProject.dispatchEvent(new Event('change')); }
      setTimeout(closeCloneModal, 800);
    } catch (e) { cloneStatus.textContent = '克隆失败: ' + e.message; cloneStatus.className = 'clone-status err'; }
    btnCloneOk.disabled = false;
  };

  (function () {
    const handle = $('treeResizer');
    if (!handle || !treePanel) return;
    let startX, startW;
    handle.addEventListener('mousedown', e => {
      e.preventDefault();
      startX = e.clientX;
      startW = treePanel.offsetWidth;
      handle.classList.add('active');
      const onMove = ev => {
        const dw = ev.clientX - startX;
        const maxW = Math.min(560, window.innerWidth * 0.55);
        const w = Math.max(180, Math.min(startW + dw, maxW));
        treePanel.style.width = w + 'px';
      };
      const onUp = () => {
        handle.classList.remove('active');
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        resizeCodeEditor();
      };
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });
  })();

  (function () {
    const handle = $('chatResizer');
    let startX, startW;
    handle.addEventListener('mousedown', e => {
      e.preventDefault(); startX = e.clientX; startW = chatPanel.offsetWidth;
      handle.classList.add('active');
      const onMove = ev => { chatPanel.style.width = Math.max(280, Math.min(startW - (ev.clientX - startX), window.innerWidth * 0.6)) + 'px'; };
      const onUp = () => {
        handle.classList.remove('active');
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        resizeCodeEditor();
      };
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });
  })();

  $('btnLogout').onclick = logout;
  $('userLabel').textContent = localStorage.getItem('userName') || '';

  loadProjects(); loadSessions();
})();

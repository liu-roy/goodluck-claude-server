(function () {
  'use strict';

  const loginForm = document.getElementById('loginForm');
  const btnLogin = document.getElementById('btnLogin');
  const errorMsg = document.getElementById('errorMsg');

  function showError(msg) {
    errorMsg.textContent = msg;
    errorMsg.classList.add('visible');
  }
  function hideError() {
    errorMsg.classList.remove('visible');
  }

  fetch('/projects/list', { method: 'GET', credentials: 'include' })
    .then(r => { if (r.ok) location.href = '/workspace.html'; })
    .catch(() => {});

  loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideError();

    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;

    if (!username || !password) {
      showError('请输入用户名和密码');
      return;
    }

    btnLogin.disabled = true;
    btnLogin.textContent = '登录中…';

    try {
      const resp = await fetch('/user/login', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });

      if (resp.status === 401) {
        const data = await resp.json().catch(() => ({}));
        showError(data.message || '用户名或密码错误');
        return;
      }

      if (!resp.ok) {
        showError('登录失败，状态码: ' + resp.status);
        return;
      }

      const data = await resp.json().catch(() => ({}));
      if (data.userCode !== undefined || (data.data && data.data.userCode !== undefined)) {
        localStorage.setItem('userCode', data.userCode || data.data?.userCode || username);
      }
      if (data.userName !== undefined || (data.data && data.data.userName !== undefined)) {
        localStorage.setItem('userName', data.userName || data.data?.userName || username);
      }

      location.href = '/workspace.html';
    } catch (err) {
      showError('网络异常: ' + err.message);
    } finally {
      btnLogin.disabled = false;
      btnLogin.textContent = '登 录';
    }
  });
})();

/**
 * GM Cred · Escritório (frontend novo)
 * Conectado nos mesmos endpoints do branch atual.
 */

const QS = new URLSearchParams(location.search);
const ENDPOINT = QS.get('api') || 'https://rarelookingorca-n8n.cloudfy.live/webhook/escritorio/estado';
const BASE = ENDPOINT.replace(/\/escritorio\/estado.*$/, '');

let chave = localStorage.getItem('escritorio_token') || '';
let demo = QS.get('demo') === '1' || (!chave && localStorage.getItem('escritorio_demo') === '1');
let verTeste = localStorage.getItem('escritorio_teste') === '1';
let conectado = false;
let ultimoErro = null;
let ultimaAtualizacao = 0;
let estado = {
  setores: [], agentes: [], leads: [], eventos: [],
  resumo: {}, numeros: [], funil: {}, financeiro: [],
  meta: null, atencao: [], crm: [], saude: {}
};
let filtroSala = 'todos';
let pollTimer = null;

// ── Utils ──────────────────────────────────────────────────
function lsGet(k) { try { return localStorage.getItem(k); } catch { return null; } }
function lsSet(k, v) { try { localStorage.setItem(k, v); } catch {} }
function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}
function fmtMoeda(n) {
  if (n == null || n === '') return '—';
  return 'R$ ' + Number(n).toLocaleString('pt-BR');
}
function hora(ts) {
  if (!ts) return '';
  const d = new Date(ts);
  return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

// ── Login / sessão ─────────────────────────────────────────
function mostrarLogin(msg) {
  document.getElementById('login').classList.remove('hidden');
  document.getElementById('chaveErr').textContent = msg || '';
  setTimeout(() => document.getElementById('chaveIn')?.focus(), 50);
}
function esconderLogin() {
  document.getElementById('login').classList.add('hidden');
}

async function entrar() {
  const v = document.getElementById('chaveIn').value.trim();
  const err = document.getElementById('chaveErr');
  if (!v) { err.textContent = 'Digite a chave.'; return; }
  err.textContent = 'Entrando…';
  try {
    const r = await fetch(BASE + '/escritorio/login', {
      method: 'POST',
      body: new URLSearchParams({ k: v })
    });
    const j = await r.json().catch(() => ({}));
    if (!r.ok || !j.token) {
      err.textContent = r.status === 401 ? 'Chave inválida.' : 'Não foi possível entrar (HTTP ' + r.status + ').';
      return;
    }
    chave = j.token;
    lsSet('escritorio_token', chave);
    demo = false;
    lsSet('escritorio_demo', '0');
    err.textContent = '';
    document.getElementById('chaveIn').value = '';
    esconderLogin();
    atualizar();
    agendar();
  } catch (e) {
    err.textContent = 'Sem conexão com o servidor.';
  }
}

function sair() {
  chave = '';
  lsSet('escritorio_token', '');
  conectado = false;
  mostrarLogin();
  setConn();
}

function verDemo() {
  demo = true;
  lsSet('escritorio_demo', '1');
  esconderLogin();
  atualizar();
  agendar();
}

// ── Conexão e polling ──────────────────────────────────────
function setConn() {
  const b = document.getElementById('connBtn');
  const a = document.getElementById('aviso');
  const txt = document.getElementById('connTxt');

  if (demo) {
    b.className = 'conn demo';
    txt.textContent = 'Demonstração';
    a.style.display = 'block';
    a.innerHTML = '<b>Modo demonstração</b>: dados fictícios. Clique em “Sair” e entre com a chave para dados reais.';
    return;
  }
  if (!chave) {
    b.className = 'conn off';
    txt.textContent = 'Bloqueado';
    a.style.display = 'block';
    a.innerHTML = '🔒 Digite a <b>chave de acesso</b> para ver os dados reais.';
    return;
  }
  b.className = 'conn ' + (conectado ? 'on' : 'off');
  txt.textContent = conectado ? 'Ao vivo' : 'Offline';
  if (!conectado) {
    a.style.display = 'block';
    a.innerHTML = 'Sem conexão com o servidor (' + esc(ultimoErro || '…') + '). Tentando de novo em breve.';
  } else if (!(estado.leads || []).length) {
    a.style.display = 'block';
    a.innerHTML = 'Escritório aberto, mas <b>sem clientes no momento</b>.';
  } else {
    a.style.display = 'none';
  }
}

async function atualizar() {
  if (demo) {
    // modo demo simples
    aplicarEstado(gerarDemo());
    conectado = true;
    ultimoErro = null;
    ultimaAtualizacao = Date.now();
    setConn();
    return;
  }
  if (!chave) {
    conectado = false;
    ultimoErro = 'sem chave';
    mostrarLogin();
    setConn();
    return;
  }
  try {
    const url = ENDPOINT + (ENDPOINT.includes('?') ? '&' : '?') +
      'k=' + encodeURIComponent(chave) +
      '&teste=' + (verTeste ? '1' : '0');
    const r = await fetch(url, { cache: 'no-store' });
    if (r.status === 401) {
      chave = '';
      lsSet('escritorio_token', '');
      conectado = false;
      ultimoErro = 'chave inválida';
      mostrarLogin('Chave inválida ou desativada.');
      setConn();
      return;
    }
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const j = await r.json();
    aplicarEstado(j);
    conectado = true;
    ultimoErro = null;
    ultimaAtualizacao = Date.now();
  } catch (e) {
    conectado = false;
    ultimoErro = e.message;
  }
  setConn();
  atualizarUpd();
}

function agendar() {
  clearInterval(pollTimer);
  const ms = demo ? 4000 : 60000;
  pollTimer = setInterval(atualizar, ms);
}

function atualizarUpd() {
  const u = document.getElementById('upd');
  if (!ultimaAtualizacao) { u.textContent = ''; return; }
  const s = Math.round((Date.now() - ultimaAtualizacao) / 1000);
  u.textContent = 'Atualizado há ' + (s < 60 ? s + 's' : Math.floor(s / 60) + ' min');
}
setInterval(atualizarUpd, 5000);

// ── Aplicar estado real ────────────────────────────────────
function aplicarEstado(j) {
  estado = {
    setores: j.setores || [],
    agentes: j.agentes || [],
    leads: j.leads || [],
    eventos: j.eventos || [],
    resumo: j.resumo || {},
    numeros: j.numeros || [],
    funil: j.funil || {},
    financeiro: j.financeiro || [],
    meta: j.meta || null,
    atencao: j.atencao || [],
    crm: j.crm || j.leads || [],
    saude: j.saude || {}
  };

  // KPIs
  const R = estado.resumo;
  document.getElementById('kAnd').textContent = R.em_andamento ?? (estado.leads.filter(l => !['fechou','parou','assinou'].includes(l.status)).length) ?? 0;
  document.getElementById('kPag').textContent = (estado.meta && estado.meta.contratos_hoje) || R.pagos_hoje || 0;
  document.getElementById('kPar').textContent = R.parados ?? 0;

  renderSalas();
  renderClientesRapido();
  renderAtencao();

  // badges
  const andamento = (estado.leads || []).filter(l => !['fechou','parou','assinou','finalizado'].includes(l.status)).length;
  const badge = document.getElementById('navBadge');
  badge.textContent = andamento;
  badge.hidden = andamento === 0;

  // se a view atual precisar re-render
  const ativa = document.querySelector('.view.ativo')?.id?.replace('v-', '');
  if (ativa === 'clientes') renderClientes();
  if (ativa === 'agentes') renderAgentes();
  if (ativa === 'conversas') renderConversas();
  if (ativa === 'financeiro') renderFinanceiro();
  if (ativa === 'numeros') renderNumeros();
}

// ── Demo fallback ──────────────────────────────────────────
function gerarDemo() {
  return {
    resumo: { em_andamento: 8, parados: 2 },
    meta: { contratos_hoje: 3 },
    setores: [
      { id: 'atendimento', nome: 'Atendimento', cor: '#2f6fed', leads_ativos: 4 },
      { id: 'formalizacao', nome: 'Formalização', cor: '#16a36a', leads_ativos: 2 },
      { id: 'recuperacao', nome: 'Recuperação', cor: '#e0a100', leads_ativos: 2 }
    ],
    agentes: [
      { id: 1, nome: 'Ana', setor_id: 'atendimento', status: 'trabalhando' },
      { id: 2, nome: 'Bruno', setor_id: 'atendimento', status: 'livre' },
      { id: 3, nome: 'Carla', setor_id: 'formalizacao', status: 'trabalhando' },
      { id: 4, nome: 'Diego', setor_id: 'recuperacao', status: 'trabalhando' }
    ],
    leads: [
      { id: 1, nome: 'Maria Silva', telefone: '(11) 98765-4321', status: 'negociando', setor_id: 'atendimento', valor: 8500 },
      { id: 2, nome: 'João Santos', telefone: '(21) 99876-5432', status: 'proposta', setor_id: 'formalizacao', valor: 12000 },
      { id: 3, nome: 'Ana Costa', telefone: '(31) 97654-3210', status: 'parou', setor_id: 'recuperacao', valor: 6200 }
    ],
    eventos: [
      { id: 1, descricao: 'Maria Silva respondeu', criado_em: new Date().toISOString(), tipo: 'respondeu' },
      { id: 2, descricao: 'João Santos recebeu proposta', criado_em: new Date(Date.now()-300000).toISOString(), tipo: 'proposta' }
    ],
    numeros: [
      { numero: '+55 11 98765-4321', origem: 'disparo', ativo: true },
      { numero: '+55 11 97654-3210', origem: 'trafego', ativo: true }
    ],
    atencao: [],
    crm: []
  };
}

// ── Renders ────────────────────────────────────────────────
function agentesDoSetor(setorId) {
  return (estado.agentes || []).filter(a => a.setor_id === setorId || a.setor === setorId);
}

function renderSalas() {
  const el = document.getElementById('salas');
  const setores = estado.setores.length ? estado.setores : [
    { id: 'geral', nome: 'Escritório', cor: '#2f6fed', leads_ativos: (estado.leads||[]).length }
  ];

  el.innerHTML = setores.map(s => {
    const ags = agentesDoSetor(s.id);
    const qtd = s.leads_ativos ?? (estado.leads || []).filter(l => l.setor_id === s.id).length;
    return `
      <div class="sala" onclick="filtrarSala('${esc(s.id)}')">
        <div class="sala-topo">
          <span class="cor" style="background:${esc(s.cor || '#2f6fed')}"></span>
          <h2>${esc(s.nome || s.id)}</h2>
          <span class="cnt">${qtd}</span>
        </div>
        <div class="cena">
          ${ags.length ? ags.map(a => `
            <div class="agente ${(a.status || '') === 'trabalhando' ? 'trabalhando' : ''}">
              <div class="avatar">${esc((a.nome || '?')[0])}</div>
              <div class="nome">${esc(a.nome || 'Agente')}</div>
            </div>
          `).join('') : '<div class="vazio" style="padding:20px">Sem agentes</div>'}
        </div>
      </div>
    `;
  }).join('');
}

function renderClientesRapido() {
  const chips = document.getElementById('filtroSalas');
  const setores = estado.setores || [];
  const opcoes = [{ id: 'todos', nome: 'Todos' }, ...setores];
  chips.innerHTML = opcoes.map(s =>
    `<button class="chip ${filtroSala === s.id ? 'ativo' : ''}" onclick="filtrarSala('${esc(s.id)}')">${esc(s.nome || s.id)}</button>`
  ).join('');

  const lista = document.getElementById('listaClientes');
  let leads = (estado.leads || []).length ? estado.leads : (estado.crm || []);
  if (filtroSala !== 'todos') {
    leads = leads.filter(l => l.setor_id === filtroSala || l.setor === filtroSala);
  }

  if (!leads.length) {
    lista.innerHTML = '<div class="vazio">Nenhum cliente no momento</div>';
    return;
  }

  lista.innerHTML = leads.slice(0, 30).map(l => {
    const st = (l.status || 'andamento').toLowerCase();
    return `
      <div class="card-cliente" onclick="abrirLead(${JSON.stringify(l).replace(/"/g, '&quot;')})">
        <div class="nome">${esc(l.nome || l.nome_cliente || 'Cliente')}</div>
        <div class="meta">
          <span>${esc(l.telefone || l.fone || '')}</span>
          <span>${l.valor ? fmtMoeda(l.valor) : ''}</span>
        </div>
        <span class="status ${esc(st)}">${esc(st)}</span>
      </div>
    `;
  }).join('');
}

function filtrarSala(id) {
  filtroSala = id;
  renderClientesRapido();
}

function renderAtencao() {
  const box = document.getElementById('atencao');
  const lista = document.getElementById('listaAtencao');
  const itens = estado.atencao || [];
  if (!itens.length) {
    box.hidden = true;
    return;
  }
  box.hidden = false;
  lista.innerHTML = itens.map(a => `
    <div style="margin-bottom:6px">
      <b>${esc(a.titulo || a.tipo || 'Alerta')}</b>
      <span style="color:var(--tx2);font-size:13px"> — ${esc(a.descricao || a.texto || '')}</span>
    </div>
  `).join('');
}

function renderClientes() {
  const lista = document.getElementById('crmLista');
  const busca = (document.getElementById('crmBusca')?.value || '').toLowerCase();
  let items = (estado.crm && estado.crm.length) ? estado.crm : (estado.leads || []);
  if (busca) {
    items = items.filter(c =>
      (c.nome || '').toLowerCase().includes(busca) ||
      (c.cpf || '').includes(busca) ||
      (c.telefone || c.fone || '').includes(busca)
    );
  }
  lista.innerHTML = items.map(c => `
    <divdiv`<div class="crm-item" onclick="abrirLead(${JSON.stringify(c).replace(/"/g, '&quot;')})">
        <div style="flex:1">
          <div style="font-weight:700">${esc(c.nome || 'Cliente')}</div>
          <div style="font-size:12px;color:var(--tx3)">${esc(c.cpf || '')} · ${esc(c.telefone || c.fone || '')}</div>
        </div>
        <span class="status ${(c.status || '').toLowerCase()}">${esc(c.status || '—')}</span>
      </div>`
  ).join('') || '<div class="vazio">Nenhum cliente encontrado</div>';
}

function renderAgentes() {
  const porSetor = {};
  (estado.agentes || []).forEach(a => {
    const s = a.setor_id || a.setor || 'geral';
    if (!porSetor[s]) porSetor[s] = [];
    porSetor[s].push(a);
  });
  const setores = Object.keys(porSetor);
  document.getElementById('agResumo').innerHTML = `
    <div class="ag-kpi"><b>${(estado.agentes || []).length}</b><span>Agentes</span></div>
    <div class="ag-kpi"><b>${setores.length}</b><span>Setores</span></div>
    <div class="ag-kpi"><b>${(estado.agentes || []).filter(a => a.status === 'trabalhando').length}</b><span>Trabalhando</span></div>
  `;
  document.getElementById('agSetores').innerHTML = setores.map(s => {
    const ags = porSetor[s];
    const nome = (estado.setores.find(x => x.id === s) || {}).nome || s;
    return `
      <div class="ag-card">
        <h4>${esc(nome)}</h4>
        <div class="stats">
          ${ags.map(a => `<div>${esc(a.nome)} — <b>${esc(a.status || 'livre')}</b></div>`).join('')}
        </div>
      </div>
    `;
  }).join('') || '<div class="vazio">Nenhum agente</div>';
}

function renderConversas() {
  const evs = estado.eventos || [];
  document.getElementById('listaConversas').innerHTML = evs.length ? evs.slice(0, 40).map(e => `
    <div class="conv-item">
      <div class="av">${esc((e.tipo || 'E')[0].toUpperCase())}</div>
      <div class="txt">
        <div class="nome">${esc(e.descricao || e.tipo || 'Evento')}</div>
        <div class="msg">${esc(e.lead_nome || '')}</div>
      </div>
      <div style="font-size:11px;color:var(--tx3)">${hora(e.criado_em)}</div>
    </div>
  `).join('') : '<div class="vazio">Nenhum evento recente</div>';
}

function renderFinanceiro() {
  const R = estado.resumo || {};
  document.getElementById('finKpis').innerHTML = `
    <div class="ag-kpi"><b>${R.em_andamento ?? '—'}</b><span>Em andamento</span></div>
    <div class="ag-kpi"><b>${(estado.meta && estado.meta.contratos_hoje) || R.pagos_hoje || '—'}</b><span>Pagos hoje</span></div>
    <div class="ag-kpi"><b>${R.parados ?? '—'}</b><span>Parados</span></div>
  `;
  const linhas = Array.isArray(estado.financeiro) ? estado.financeiro : [];
  document.getElementById('finTabela').innerHTML = linhas.length ? `
    <table>
      <thead><tr><th>Item</th><th>Valor</th></tr></thead>
      <tbody>
        ${linhas.map(l => `<tr><td>${esc(l.descricao || l.nome || JSON.stringify(l))}</td><td>${esc(l.valor ?? '')}</td></tr>`).join('')}
      </tbody>
    </table>
  ` : '<div class="vazio">Sem dados financeiros detalhados neste payload</div>';
}

function renderNumeros() {
  const nums = estado.numeros || [];
  document.getElementById('numerosLista').innerHTML = nums.length ? nums.map(n => `
    <div class="num-card">
      <div class="num">${esc(n.numero || n.numero || n.fone || '—')}</div>
      <div class="origem">Origem: ${esc(n.origem || '—')}</div>
      <span class="st ${(n.ativo || n.status === 'ativo') ? 'ativo' : 'inativo'}">${(n.ativo || n.status === 'ativo') ? 'ativo' : 'inativo'}</span>
    </div>
  `).join('') : '<div class="vazio">Nenhum número retornado</div>';
}

// ── Modal ──────────────────────────────────────────────────
function abrirLead(l) {
  if (typeof l === 'string') {
    try { l = JSON.parse(l.replace(/&quot;/g, '"')); } catch { return; }
  }
  document.getElementById('modalTitulo').textContent = l.nome || l.nome_cliente || 'Cliente';
  document.getElementById('modalBody').innerHTML = `
    <p><strong>Telefone:</strong> ${esc(l.telefone || l.fone || '—')}</p>
    <p><strong>Status:</strong> ${esc(l.status || '—')}</p>
    <p><strong>Setor:</strong> ${esc(l.setor_id || l.setor || '—')}</p>
    <p><strong>Valor:</strong> ${l.valor ? fmtMoeda(l.valor) : '—'}</p>
    <p><strong>CPF:</strong> ${esc(l.cpf || '—')}</p>
    <p style="margin-top:12px;color:var(--tx3);font-size:13px">ID: ${esc(l.id || '')}</p>
  `;
  document.getElementById('modal').hidden = false;
}
function fecharModal() {
  document.getElementById('modal').hidden = true;
}

// ── Navegação ──────────────────────────────────────────────
function mostrarView(nome) {
  document.querySelectorAll('.view').forEach(v => {
    v.classList.remove('ativo');
    v.hidden = true;
  });
  const view = document.getElementById('v-' + nome);
  if (view) {
    view.hidden = false;
    view.classList.add('ativo');
  }
  document.querySelectorAll('.nv[data-view]').forEach(btn => {
    btn.classList.toggle('ativo', btn.dataset.view === nome);
  });
  const titulos = {
    escritorio: ['Escritório', 'Visão geral'],
    clientes: ['Clientes', 'CRM'],
    agentes: ['Agentes', 'Equipe'],
    conversas: ['Eventos', 'Últimas interações'],
    financeiro: ['Financeiro', 'Resumo'],
    numeros: ['Números', 'WhatsApp']
  };
  const t = titulos[nome] || ['Painel', ''];
  document.getElementById('pgTit').textContent = t[0];
  document.getElementById('pgSub').textContent = t[1];

  if (nome === 'clientes') renderClientes();
  if (nome === 'agentes') renderAgentes();
  if (nome === 'conversas') renderConversas();
  if (nome === 'financeiro') renderFinanceiro();
  if (nome === 'numeros') renderNumeros();
}

// ── Tema ───────────────────────────────────────────────────
function toggleTema() {
  const escuro = document.documentElement.dataset.tema === 'escuro';
  if (escuro) {
    delete document.documentElement.dataset.tema;
    lsSet('gmcred_tema', 'claro');
  } else {
    document.documentElement.dataset.tema = 'escuro';
    lsSet('gmcred_tema', 'escuro');
  }
}

// ── Init ───────────────────────────────────────────────────
function init() {
  document.getElementById('btnEntrar').addEventListener('click', entrar);
  document.getElementById('chaveIn').addEventListener('keydown', e => { if (e.key === 'Enter') entrar(); });
  document.getElementById('btnDemo').addEventListener('click', verDemo);
  document.getElementById('btnSair').addEventListener('click', sair);
  document.getElementById('btnTema').addEventListener('click', toggleTema);
  document.getElementById('btnTeste').addEventListener('click', () => {
    verTeste = !verTeste;
    lsSet('escritorio_teste', verTeste ? '1' : '0');
    atualizar();
  });
  document.getElementById('modalFechar').addEventListener('click', fecharModal);
  document.getElementById('modal').addEventListener('click', e => { if (e.target.id === 'modal') fecharModal(); });
  document.getElementById('crmBusca')?.addEventListener('input', renderClientes);

  document.querySelectorAll('.nv[data-view]').forEach(btn => {
    btn.addEventListener('click', () => mostrarView(btn.dataset.view));
  });

  if (!chave && !demo) {
    mostrarLogin();
  } else {
    esconderLogin();
    atualizar();
    agendar();
  }
}

document.addEventListener('DOMContentLoaded', init);

// expõe para onclick no HTML
window.filtrarSala = filtrarSala;
window.abrirLead = abrirLead;

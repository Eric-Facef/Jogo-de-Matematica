// ─── CONFIGURAÇÃO DE AMBIENTE ─────────────────────────────────────────────────
// LOCAL: descomente a linha abaixo e comente a linha PRODUÇÃO
 const BASE_URL = 'http://localhost:8080';

// PRODUÇÃO (Render): deixe assim para subir
//const BASE_URL = '';
// ─────────────────────────────────────────────────────────────────────────────

let stompClient = null;
let codigoSalaAtual = null;
let minhaFuncao = null;
let jogoEmAndamento = false;
let tentativasReconexao = 0;
const MAX_RECONEXOES = 5;

// ─── TELA ────────────────────────────────────────────────────────────────────

function changeScreen(screenId) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    const target = document.getElementById(screenId);
    if (target) target.classList.add('active');
}

function sairDoTerminal() {
    if (jogoEmAndamento) {
        alert("⚠️ Protocolo ativo! Não é possível sair do terminal durante a missão.");
        return;
    }
    changeScreen('screen-setup');
}

// ─── CONEXÃO ─────────────────────────────────────────────────────────────────

function criarNovaSala() {
    fetch(`${BASE_URL}/api/sala/criar`, { method: 'POST' })
        .then(res => res.json())
        .then(sala => {
            document.getElementById('inputCodigoSala').value = sala.codigo;
            document.getElementById('lobbyStatusMsg').innerText = `Sala Criada: ${sala.codigo}. Insira seu codinome e clique em ENTRAR.`;
            document.getElementById('inputNomeEquipe').disabled = false;
        })
        .catch(() => alert("Erro ao conectar com o servidor."));
}

function conectarAoLobby() {
    const nome = document.getElementById('inputNomeJogador').value.trim();
    const codigo = document.getElementById('inputCodigoSala').value.trim().toUpperCase();

    if (!nome || !codigo) {
        alert("Preencha seu codinome de agente e o código de acesso da sala!");
        return;
    }

    sessionStorage.setItem('nomeAgente', nome);
    sessionStorage.setItem('codigoSala', codigo);
    sessionStorage.setItem('minhaFuncao', minhaFuncao || '');
    const nomeEquipe = document.getElementById('inputNomeEquipe') ? document.getElementById('inputNomeEquipe').value.trim() : '';
    sessionStorage.setItem('nomeEquipe', nomeEquipe);

    codigoSalaAtual = codigo;
    _iniciarConexaoStomp(nome, codigo);
}

function _iniciarConexaoStomp(nome, codigo) {
    const socket = new SockJS(`${BASE_URL}/ws-jogo`);
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function () {
        tentativasReconexao = 0;
        document.getElementById('lobbyStatusMsg').innerText = `Conectado à sala: ${codigo}`;
        document.getElementById('lobbyStatusMsg').style.color = "var(--green-glow)";
        document.getElementById('area-selecao-funcoes').style.display = "grid";

        stompClient.subscribe(`/topic/sala/${codigo}`, function (resposta) {
            const pacote = JSON.parse(resposta.body);
            if (pacote.sala) {
                sincronizarEstadoJogo(pacote.sala, pacote.rankingGeral);
            } else if (pacote.erro) {
                mostrarErro(pacote.erro);
            }
        });

        stompClient.send(`/app/sala/${codigo}/acao`, {}, JSON.stringify({
            tipo: 'ENTRAR',
            nomeJogador: nome,
            nomeEquipe: sessionStorage.getItem('nomeEquipe') || ''
        }));

        const funcaoSalva = sessionStorage.getItem('minhaFuncao');
        if (funcaoSalva && funcaoSalva !== '' && funcaoSalva !== 'null') {
            minhaFuncao = funcaoSalva;
        }

    }, function () {
        if (tentativasReconexao < MAX_RECONEXOES) {
            tentativasReconexao++;
            const msg = document.getElementById('lobbyStatusMsg');
            if (msg) {
                msg.innerText = `⚠️ Conexão perdida. Reconectando... (${tentativasReconexao}/${MAX_RECONEXOES})`;
                msg.style.color = "#f87171";
            }
            setTimeout(() => _iniciarConexaoStomp(nome, codigo), 3000);
        } else {
            const msg = document.getElementById('lobbyStatusMsg');
            if (msg) {
                msg.innerText = "❌ Não foi possível reconectar. Recarregue a página.";
                msg.style.color = "#f87171";
            }
        }
    });
}

// ─── LOBBY ───────────────────────────────────────────────────────────────────

function escolherFuncaoRede(funcao) {
    stompClient.send(`/app/sala/${codigoSalaAtual}/acao`, {}, JSON.stringify({
        tipo: 'ESCOLHER_FUNCAO',
        funcaoDefinida: funcao
    }));
}

function chooseRoleLocal(funcao) {
    minhaFuncao = funcao;
    sessionStorage.setItem('minhaFuncao', funcao);
    escolherFuncaoRede(funcao);

    const statusMsg = document.getElementById('lobbyStatusMsg');
    statusMsg.innerText = `Função [${funcao}] selecionada! Aguardando o Host iniciar o protocolo...`;
    statusMsg.style.color = "#fbbf24";
}

function enviarSinalInicio() {
    stompClient.send(`/app/sala/${codigoSalaAtual}/acao`, {}, JSON.stringify({
        tipo: 'INICIAR_JOGO'
    }));
}

// ─── OPERADOR ─────────────────────────────────────────────────────────────────

function cortarFio(cor) {
    const nomesFios = { AZUL: 'Azul', VERMELHO: 'Vermelho', AMARELO: 'Amarelo', VERDE: 'Verde' };
    const cores = { AZUL: '#1e3a8a', VERMELHO: '#991b1b', AMARELO: '#78350f', VERDE: '#14532d' };

    const modal = document.getElementById('modal-confirmar-fio');
    const textoModal = document.getElementById('modal-fio-nome');
    const btnModal = document.getElementById('modal-fio-btn');

    textoModal.innerText = `Confirmar corte do Fio ${nomesFios[cor] || cor}?`;
    btnModal.style.background = cores[cor] || '#333';
    btnModal.onclick = function () {
        modal.style.display = 'none';
        stompClient.send(`/app/sala/${codigoSalaAtual}/acao`, {}, JSON.stringify({
            tipo: 'CORTAR_FIO',
            funcaoDefinida: cor
        }));
    };
    modal.style.display = 'flex';
}

function fecharModalFio() {
    document.getElementById('modal-confirmar-fio').style.display = 'none';
}

// ── FASE 2: ALINHAMENTO QUÂNTICO ─────────────────────────────────────────────

function inserirSequenciaQuadrantes() {
    const v1 = document.getElementById('quadrante1') ? document.getElementById('quadrante1').value.trim() : '';
    const v2 = document.getElementById('quadrante2') ? document.getElementById('quadrante2').value.trim() : '';
    const v3 = document.getElementById('quadrante3') ? document.getElementById('quadrante3').value.trim() : '';

    if (!v1 || !v2 || !v3) return mostrarErro("Preencha os três quadrantes!");

    stompClient.send(`/app/sala/${codigoSalaAtual}/acao`, {}, JSON.stringify({
        tipo: 'INSERIR_SEQUENCIA',
        funcaoDefinida: `${v1},${v2},${v3}`
    }));
}

// ── FASE 3: TOKENS ────────────────────────────────────────────────────────────

function validarChaveAlfa() {
    const input = document.getElementById('chaveAlfaInput');
    if (!input || !input.value.trim()) return mostrarErro("Insira o valor da Chave Alfa!");

    stompClient.send(`/app/sala/${codigoSalaAtual}/acao`, {}, JSON.stringify({
        tipo: 'INTERCEPTAR_DADOS',
        nomeJogador: input.value.trim().toUpperCase()
    }));
    input.value = '';
}

function validarTokenInfect() {
    const input = document.getElementById('tokenInfectInput');
    if (!input || !input.value.trim()) return mostrarErro("Insira o Token de Infecção!");

    stompClient.send(`/app/sala/${codigoSalaAtual}/acao`, {}, JSON.stringify({
        tipo: 'INTERCEPTAR_TOKEN',
        nomeJogador: input.value.trim().toUpperCase()
    }));
    input.value = '';
}

function avancarParaPainelC() {
    stompClient.send(`/app/sala/${codigoSalaAtual}/acao`, {}, JSON.stringify({
        tipo: 'AVANCAR_PAINEL_C'
    }));
}

// ── Token enviado pelo Investigador diretamente da sua tela ──────────────────
function enviarTokenInvestigador() {
    const input = document.getElementById('tokenInvestigadorInput');
    if (!input || !input.value.trim()) return mostrarErro("Insira o Token de Infecção!");

    stompClient.send(`/app/sala/${codigoSalaAtual}/acao`, {}, JSON.stringify({
        tipo: 'INTERCEPTAR_TOKEN',
        nomeJogador: input.value.trim()
    }));
    input.value = '';
}

// ─── INVESTIGADOR ─────────────────────────────────────────────────────────────

function verificarBlocoNotas() {
    const notas = document.getElementById('blocoNotas');
    if (notas) {
        notas.style.borderColor = "var(--green-glow)";
        setTimeout(() => notas.style.borderColor = "#333", 1500);
    }
}

function tentarDesarmarFinal() {
    const inputCodigo = document.getElementById('inputCodigo');
    const inputValor = document.getElementById('inputValor');
    const inputToken = document.getElementById('inputTokenFase4');
    const codigo = inputCodigo ? inputCodigo.value.trim() : '';
    const valor = inputValor ? inputValor.value.trim() : '';
    const token = inputToken ? inputToken.value.trim() : '';

    if (!codigo || !valor || !token) return mostrarErro("Preencha todos os parâmetros de desarme do Painel C!");

    if (!/^\d+(-\d+){4}$/.test(codigo)) {
        return mostrarErro("Formato inválido! Use o padrão X-X-X-X-X (ex: 57-6-18-7-31)");
    }

    if (!/^\d+$/.test(valor)) {
        return mostrarErro("O Escalar deve ser um número inteiro.");
    }

    if (!/^\d+$/.test(token)) {
        return mostrarErro("O Token de Verificação deve ser um número inteiro.");
    }

    stompClient.send(`/app/sala/${codigoSalaAtual}/acao`, {}, JSON.stringify({
        tipo: 'TENTAR_DESARME',
        funcaoDefinida: `${codigo},${valor},${token}`
    }));
}

// ─── FEEDBACK ────────────────────────────────────────────────────────────────

function mostrarErro(msg) {
    const toast = document.getElementById('toast-erro');
    const toastMsg = document.getElementById('toast-erro-msg');
    if (!toast || !toastMsg) { alert(msg); return; }
    toastMsg.innerText = msg;
    toast.style.display = 'flex';
    setTimeout(() => toast.style.display = 'none', 3500);
}

function atualizarContadorErros(falhas) {
    const contador = document.getElementById('contador-erros');
    if (!contador) return;
    contador.innerText = `⚠️ Falhas: ${falhas}/3`;
    contador.style.color = falhas >= 2 ? '#f87171' : '#fbbf24';
}

// ─── SINCRONIZAÇÃO CENTRAL ───────────────────────────────────────────────────

function sincronizarEstadoJogo(sala, rankingGeral) {
    // 1. Timer
    const minutos = Math.floor(sala.tempoRestante / 60);
    const segundos = sala.tempoRestante % 60;
    const timerEl = document.getElementById('timerDisplay');
    if (timerEl) {
        timerEl.innerText = `${minutos.toString().padStart(2, '0')}:${segundos.toString().padStart(2, '0')}`;
        timerEl.style.color = sala.tempoRestante <= 300 ? '#ff6b6b' : 'var(--accent-color)';
    }

    // 2. Contador de erros
    atualizarContadorErros(sala.falhasGlobais || 0);

    // 3. Lockdown
    const lockdownUI = document.getElementById('lockdown-screen');
    if (sala.lockdownAtivo) {
        lockdownUI.style.display = 'flex';
        document.getElementById('lockdown-timer').innerText = sala.tempoLockdown ?? "30";
        return;
    } else {
        lockdownUI.style.display = 'none';
    }

    // 4. Bloqueia nome da equipe para quem entrou (não criou)
    const inputEquipe = document.getElementById('inputNomeEquipe');
    if (inputEquipe && sala.nomeEquipe) {
        inputEquipe.value = sala.nomeEquipe;
        inputEquipe.disabled = true;
    }

    // 5. Lobby — botão de início e bloqueio de funções
    const nomeUsuarioLogado = document.getElementById('inputNomeJogador').value.trim();
    const totalFuncoes = Object.values(sala.jogadores || {}).map(j => j.funcao).filter(f => f && f !== 'ESPECTADOR');
    const funcoesUnicas = new Set(totalFuncoes);

    if (funcoesUnicas.size === 4 && sala.nomeHost === nomeUsuarioLogado && !sala.jogoIniciado) {
        document.getElementById('btnIniciarJogoRede').style.display = 'block';
    } else {
        document.getElementById('btnIniciarJogoRede').style.display = 'none';
    }

    Object.values(sala.jogadores || {}).forEach(j => {
        if (j.funcao && j.nome !== nomeUsuarioLogado) {
            const btn = document.getElementById(`btn-funcao-${j.funcao}`);
            if (btn) {
                btn.disabled = true;
                btn.innerText = `[OCUPADO] - ${j.nome}`;
                btn.style.opacity = "0.5";
            }
        }
    });

    // 5. Mudança de tela ao iniciar
    if (sala.jogoIniciado && !sala.jogoDesarmado) {
        jogoEmAndamento = true;
        if (minhaFuncao === 'OPERADOR')     changeScreen('screen-operador');
        if (minhaFuncao === 'ESPECIALISTA') changeScreen('screen-especialista');
        if (minhaFuncao === 'ANALISTA')     changeScreen('screen-analistas');
        if (minhaFuncao === 'INVESTIGADOR') changeScreen('screen-investigador');
    }

    // 6. Sub-fases do Operador
    // Fase 1: Fios | Fase 2: Alinhamento Quântico | Fase 3: Tokens | Fase 4: Aguardando Desarme
    if (minhaFuncao === 'OPERADOR' && sala.jogoIniciado) {
        const f1 = document.getElementById('op-fase1');
        const f2 = document.getElementById('op-fase2');
        const f3 = document.getElementById('op-fase3');
        const f4 = document.getElementById('op-fase4');
        const btnAvancar = document.getElementById('btnAvancarPainelC');

        if (sala.faseAtualOperador === 1) {
            f1.style.display = 'block'; f2.style.display = 'none';
            f3.style.display = 'none';  f4.style.display = 'none';

        } else if (sala.faseAtualOperador === 2) {
            f1.style.display = 'none'; f2.style.display = 'block';
            f3.style.display = 'none'; f4.style.display = 'none';

        } else if (sala.faseAtualOperador === 3) {
            f1.style.display = 'none'; f2.style.display = 'none';
            f3.style.display = 'block'; f4.style.display = 'none';

            if (sala.dadosInterceptadosFase2 && sala.dadosInterceptadosFase3) {
                btnAvancar.style.display = 'block';
            } else {
                btnAvancar.style.display = 'none';
            }
            const chaveOk = document.getElementById('chave-alfa-ok');
            const tokenOk = document.getElementById('token-infect-ok');
            if (chaveOk) chaveOk.style.display = sala.dadosInterceptadosFase2 ? 'inline' : 'none';
            if (tokenOk) tokenOk.style.display = sala.dadosInterceptadosFase3 ? 'inline' : 'none';

        } else if (sala.faseAtualOperador === 4) {
            f1.style.display = 'none'; f2.style.display = 'none';
            f3.style.display = 'none'; f4.style.display = 'block';
        }
    }

    // 7. Analista — fases
    // Fase 1: standby | Fase 2: mostra figuras/regras para descobrir a ordem | Fase 3+: Chave Alfa
    if (minhaFuncao === 'ANALISTA') {
        const pistaF1  = document.getElementById('pista-fase1-analista-aguardo');
        const pistaF2  = document.getElementById('pista-fase2-analista-quantico');
        const pistaF3  = document.getElementById('pista-fase2-analista'); // Chave Alfa (era fase 2, agora fase 3)

        if (pistaF1)  pistaF1.style.display  = sala.faseAtualOperador < 2  ? 'block' : 'none';
        if (pistaF2)  pistaF2.style.display  = sala.faseAtualOperador === 2 ? 'block' : 'none';
        if (pistaF3)  pistaF3.style.display  = sala.faseAtualOperador >= 3  ? 'block' : 'none';
    }

    // 8. Investigador — fases
    // Fase 3+: token de infecção | Fase 4: formulário de desarme
    if (minhaFuncao === 'INVESTIGADOR') {
        const pistaInv = document.getElementById('pista-fase2-investigador');
        const formDes  = document.getElementById('formulario-desarme-final');
        if (pistaInv) pistaInv.style.display = sala.faseAtualOperador >= 3 ? 'block' : 'none';
        if (formDes)  formDes.style.display  = sala.faseAtualOperador === 4 ? 'block' : 'none';
    }

    // 9. Especialista — fase 4 (calibração final)
    if (minhaFuncao === 'ESPECIALISTA') {
        const pistaEsp = document.getElementById('pista-fase3-especialista');
        if (pistaEsp) pistaEsp.style.display = sala.faseAtualOperador === 4 ? 'block' : 'none';
    }

    // 10. Derrota (tempo esgotado)
    if (sala.jogoEncerradoPorTempo && !sala.jogoDesarmado) {
        jogoEmAndamento = false;
        sessionStorage.clear();
        changeScreen('screen-derrota');
        return;
    }

    // 11. Vitória
    if (sala.jogoDesarmado) {
        jogoEmAndamento = false;
        sessionStorage.clear();
        changeScreen('screen-vitoria');
        const corpo = document.getElementById('corpoRanking');
        if (corpo && rankingGeral) {
            corpo.innerHTML = '';
            rankingGeral.forEach((item, index) => {
                const tr = document.createElement('tr');
                if (item.sala === codigoSalaAtual) tr.style.background = "rgba(34, 197, 94, 0.2)";
                tr.innerHTML = `
                    <td><strong>${index + 1}º</strong></td>
                    <td>${item.nomeEquipe || item.sala}</td>
                    <td>${item.tempoGasto}s</td>
                    <td>${item.errosCometidos || 0}</td>
                `;
                corpo.appendChild(tr);
            });
        }
    }
}
<div align="center">

# ⬡ Kyron AI

**Chatbot local para modelos de linguagem open source via Ollama**

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)](https://www.docker.com/)
[![Ollama](https://img.shields.io/badge/Ollama-Local_AI-000000?style=flat-square)](https://ollama.com/)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)

Uma chatbot para conversar com modelos de linguagem rodando localmente.  
Seus dados ficam no seu hardware. Sem APIs pagas. Sem envio de dados para servidores externos.

</div>

---

## ✨ Funcionalidades

- 💬 **Chat em tempo real** com streaming de respostas token a token
- 🧠 **Memória persistente** — salva fatos sobre o usuário para personalizar respostas
- 📁 **Projetos** — crie espaços de contexto com arquivos PDF, DOCX, TXT e MD
- 📎 **Anexo de arquivos** — envie documentos diretamente no chat
- 👁 **Suporte a modelos Vision** — envie imagens para modelos multimodais
- 💭 **Thinking Mode** — visualize o raciocínio de modelos como DeepSeek e Qwen3
- 🌐 **Busca na web** — pesquisa em tempo real via SearXNG (self-hosted, sem rastreamento)
- 📌 **Chats fixados** — fixe até 3 conversas importantes no topo da sidebar
- ✏️ **Renomear conversas** — dê títulos personalizados aos seus chats
- 🏷️ **Tags automáticas** — identifica modelos Cloud, Vision, Think, Tools e Embedding
- 🌗 **Tema claro e escuro** — com cor de ênfase personalizável
- 🌍 **Idioma de resposta** — force o modelo a responder em qualquer idioma
- 🔍 **Busca no histórico** — encontre conversas antigas rapidamente
- ⏹️ **Parar geração** — interrompa a resposta a qualquer momento

---

## 🛠️ Tecnologias

| Camada | Tecnologia |
|---|---|
| Frontend | HTML + CSS + JavaScript (Vanilla) |
| Backend | Java 17 + Spring Boot 3.2 + Spring WebFlux |
| Banco de dados | PostgreSQL 16 |
| IA Local | Ollama |
| Busca Web | SearXNG (self-hosted) |
| Infraestrutura | Docker + Docker Compose |

---

## 📋 Pré-requisitos

Você **não precisa ter Java instalado**. Tudo roda dentro do Docker.

O que você precisa:

- **[Docker](https://docs.docker.com/get-docker/)** com Docker Compose
- **[Ollama](https://ollama.com/download)** instalado e rodando
- Pelo menos **um modelo baixado** no Ollama

---

## 🚀 Instalação

### 1. Clone o repositório

```bash
git clone https://github.com/Otavio2704/OpenChat.git
cd openchat
```

### 2. Configure o ambiente (opcional)

```bash
cp docker-compose.override.yml.example docker-compose.override.yml
```

> Por padrão já funciona sem alterações. O `.override.yml` é para customizações como senha do banco ou porta.

### 3. Configure o SearXNG (busca web)

```bash
# Gere uma secret key
openssl rand -hex 32

# Crie o arquivo de configuração
cp searxng-config/settings.yml.example searxng-config/settings.yml
```

Edite o `searxng-config/settings.yml` e substitua o campo `secret_key` pelo valor gerado:

```yaml
server:
  secret_key: "COLE_AQUI_O_OUTPUT_DO_OPENSSL"
```

> **Nota:** O arquivo `settings.yml` está no `.gitignore` — sua secret key nunca será enviada ao repositório.

### 4. Baixe um modelo no Ollama

```bash
ollama pull qwen3.5:9b
```

> **⚠️ Recomendação:** Caso tenha um hardware humilde, use um modelo cloud:
> ```bash
> ollama pull qwen3.5:cloud
> ```

Veja todos os modelos disponíveis em [ollama.com/search](https://ollama.com/search).

### 5. Suba os containers

```bash
docker compose up -d
```

Aguarde ~30 segundos e acesse `http://localhost:8080`.

---

## 🌐 Busca na Web

O OpenChat integra o **SearXNG** — um meta-buscador open source, self-hosted, sem rastreamento e sem necessidade de chave de API.

### Como funciona

Quando a busca web está ativada, o sistema:

1. Envia a query do usuário ao SearXNG
2. Coleta os top 5 resultados com título, snippet e URL
3. Injeta o contexto no prompt enviado ao modelo
4. Acumula os contextos ao longo da conversa — o modelo não "esquece" buscas anteriores

### Cascata de provedores

Se o SearXNG estiver offline, o sistema tenta automaticamente:

```
SearXNG (local) → DuckDuckGo HTML → DuckDuckGo Instant Answer
```

### Como usar

1. Clique no ícone 🌐 na topbar para ativar a busca web
2. Envie sua mensagem normalmente
3. O modelo responderá com base nos resultados reais da web
4. Pergunte *"quais foram as fontes?"* a qualquer momento — o contexto é mantido

---

## ⚙️ Configuração do Ollama

O Ollama precisa aceitar conexões externas para que o container Docker consiga se comunicar com ele.

### Linux

```bash
sudo mkdir -p /etc/systemd/system/ollama.service.d

sudo tee /etc/systemd/system/ollama.service.d/override.conf > /dev/null << 'EOF'
[Service]
Environment="OLLAMA_HOST=0.0.0.0"
EOF

sudo systemctl daemon-reload
sudo systemctl restart ollama
```

**Importante no Linux:** libere a porta do Ollama no firewall para as redes Docker:

```bash
sudo iptables -I INPUT -s 172.18.0.0/16 -p tcp --dport 11434 -j ACCEPT
sudo iptables -I INPUT -s 172.17.0.0/16 -p tcp --dport 11434 -j ACCEPT

# Persiste as regras (sobrevive a reinicializações)
sudo apt install iptables-persistent -y
sudo netfilter-persistent save
```

### macOS

```bash
launchctl setenv OLLAMA_HOST "0.0.0.0"
# Reinicie o Ollama após o comando
```

### Windows (PowerShell)

```powershell
[System.Environment]::SetEnvironmentVariable('OLLAMA_HOST', '0.0.0.0', 'User')
# Reinicie o Ollama após o comando
```

---

## 🐳 Comandos Docker úteis

```bash
# Subir os serviços
docker compose up -d

# Ver logs em tempo real
docker compose logs -f

# Ver logs só do backend
docker compose logs -f backend

# Ver logs do SearXNG
docker compose logs -f searxng

# Parar tudo (preserva os dados do banco)
docker compose down

# Parar e apagar todos os dados
docker compose down -v

# Rebuildar após mudanças no código
docker compose up -d --build
```

---

## 📁 Estrutura do Projeto

```
kyron-ai/
├── backend/
│   ├── src/main/java/otavio/openchat/
│   │   ├── config/       # CORS, WebClient
│   │   ├── controller/   # Chat, History, Models, Files, Memory, Projects
│   │   ├── service/      # OllamaService, ConversationService, MemoryService, ProjectService
│   │   ├── repository/   # JPA Repositories
│   │   └── model/        # Entidades JPA + DTOs
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── index.html
│   ├── style.css
│   └── app.js
├── searxng-config/
│   ├── settings.yml          # gitignored — contém sua secret key
│   └── settings.yml.example  # template para novos colaboradores
├── docker-compose.yml
├── docker-compose.override.yml.example
└── README.md
```

---

## 🧠 Como funciona a Memória

1. Clique no ícone 🧠 na topbar
2. Adicione fatos: *"Prefiro respostas diretas"*, *"Trabalho com Java e Spring Boot"*
3. Organize por categoria: Preferência, Contexto, Habilidade, Projeto
4. Ative ou desative memórias individualmente

Todas as memórias ativas são injetadas automaticamente no contexto de cada conversa.

---

## 📂 Como funcionam os Projetos

1. Na sidebar, clique em **+** ao lado de "Projetos"
2. Dê um nome e descrição ao projeto
3. Adicione arquivos (PDF, DOCX, TXT, MD) ou textos livres
4. Clique em **"Iniciar chat com este projeto"**

O conteúdo dos arquivos é injetado automaticamente no contexto de cada mensagem enviada.

---

## 🤝 Contribuindo

Contribuições são bem-vindas!

1. Fork o repositório
2. Crie uma branch: `git checkout -b feature/minha-feature`
3. Commit: `git commit -m 'feat: adiciona minha feature'`
4. Push: `git push origin feature/minha-feature`
5. Abra um Pull Request

---

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---

<div align="center">
Feito por Otávio Guedes <code>Dev Backend Java</code>
</div>

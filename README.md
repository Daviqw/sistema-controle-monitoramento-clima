# Sistema de Controle e Monitoramento do Clima

## Como rodar no VS Code

### 1. Abrir o projeto

Baixe e extraia o projeto. No VS Code, selecione **Arquivo > Abrir Pasta** e abra a pasta que contém o arquivo `pom.xml`.

### 2. Configurar na primeira execução

No PostgreSQL, crie um banco vazio chamado `clima`.

No VS Code, abra **Terminal > Novo Terminal**, selecione **PowerShell** e execute:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\configurar-windows.ps1
```

Informe o usuário e a senha do PostgreSQL, o e-mail e a senha inicial do administrador.

Se o projeto já estiver configurado nessa máquina e o arquivo `.config-local.json` existir, pule esta etapa. Não recrie o banco nem substitua a configuração existente.

### 3. Iniciar o backend

No terminal do VS Code, execute:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\iniciar-backend.ps1
```

Aguarde a mensagem de que a aplicação iniciou. Na primeira execução em um banco novo, as tabelas e os dados iniciais são criados automaticamente.

### 4. Iniciar a interface

Clique no botão **+** do painel de terminal para abrir outro PowerShell na mesma pasta, mantendo o backend aberto. Execute:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\iniciar-frontend.ps1
```

A primeira execução instala as dependências e compila a interface. Nas próximas aberturas, a compilação é reutilizada enquanto o código não mudar.

### 5. Acessar e encerrar

Abra **http://localhost:4200** no navegador e entre com o administrador definido na configuração. Os outros usuários são cadastrados pelo administrador.

Mantenha os dois terminais abertos durante o uso. Para encerrar, pressione **Ctrl+C** em cada terminal.

### Depois de alterar o código

Encerre e execute novamente o iniciador da parte alterada. Ele identifica as mudanças e recompila quando necessário. Atualize o navegador com **Ctrl+F5**.

Para editar a interface com atualização automática, use este comando no lugar do iniciador normal do frontend:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\iniciar-frontend.ps1 -Desenvolvimento
```

Os dados ficam no PostgreSQL. O arquivo `.config-local.json` é exclusivo da máquina e não deve ser enviado ao GitHub.

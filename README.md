# Sistema de Controle e Monitoramento do Clima

## Requisitos

Java 21, Maven, Node.js com npm e PostgreSQL instalados.

## Como executar

Abra o PowerShell na pasta do projeto.

Na primeira configuração, crie o banco `clima` no PostgreSQL e execute:

```powershell
.\configurar-windows.ps1
```

Se o arquivo `.config-local.json` já existir, mantenha-o e pule essa etapa.

Para iniciar o backend:

```powershell
.\iniciar-backend.ps1
```

Em outro PowerShell, na mesma pasta, inicie o frontend:

```powershell
.\iniciar-frontend.ps1
```

Acesse http://localhost:4200 e entre com o usuário configurado.
Mantenha os dois terminais abertos. Para encerrar, pressione Ctrl+C em cada um.

Os cadastros ficam no PostgreSQL. O arquivo `.config-local.json` contém a configuração local e não deve ser compartilhado na entrega.

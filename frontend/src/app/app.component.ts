import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

interface CadastroItem { id: string; status: string; [key: string]: any; }
interface CampoCadastro { key: string; label: string; required?: boolean; options?: string; multi?: boolean; }
interface LoginResponse { token: string; tipo: string; expiraEmMs: number; email: string; perfis: string[]; }
interface Usuario { id: string; nome: string; email: string; ativo: boolean; perfis: string[]; }
interface UsuarioPage { content: Usuario[]; page: number; size: number; totalElements: number; }
interface UsuarioForm { nome: string; email: string; senha: string; perfis: string[]; }

@Component({
  selector: 'app-root', standalone: true, imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {
  loggedIn = !!sessionStorage.getItem('clima_token');
  busy = false;
  email = 'admin@clima.local';
  password = '';
  currentEmail = sessionStorage.getItem('clima_email') || '';
  roles: string[] = JSON.parse(sessionStorage.getItem('clima_roles') || '[]') as string[];
  currentUser: { id: string; nome: string; email: string; ativo: boolean; perfis: string[] } | null = null;
  section = 'dashboard';
  errorMessage = '';
  successMessage = '';
  userCount: number | null = null;
  users: Usuario[] = [];
  totalUsers = 0;
  filterText = '';
  showCreate = false;
  selectedRole = 'USUARIO';
  newUser: UsuarioForm = { nome: '', email: '', senha: '', perfis: ['USUARIO'] };

  cadastros = [{key:'ogms',label:'Órgãos (OGM)'},{key:'dmcs',label:'Distritos (DMC)'},{key:'redes',label:'Redes de monitoramento'},{key:'tecnicos',label:'Técnicos'},{key:'estacoes',label:'Estações meteorológicas'}];
  cadastroKey = 'ogms'; cadastroLabel = 'Órgãos (OGM)';
  cadastroItems: CadastroItem[] = []; showCadastroForm=false; editingId='';
  cadastroForm: Record<string,string> = {};
  opcoes: Record<string,CadastroItem[]> = {ogms:[],dmcs:[],redes:[],estacoes:[]};
  camposPorTipo: Record<string,CampoCadastro[]> = {
    ogms:[{key:'sigla',label:'Sigla',required:true},{key:'nome',label:'Nome',required:true},{key:'email_central',label:'E-mail'},{key:'telefone_central',label:'Telefone'},{key:'diretor_geral_nome',label:'Diretor-geral',required:true}],
    dmcs:[{key:'ogm_id',label:'Órgão gestor (OGM)',required:true,options:'ogms'},{key:'nome',label:'Nome',required:true},{key:'email_central',label:'E-mail'},{key:'telefone_central',label:'Telefone'},{key:'meteorologista_chefe_nome',label:'Meteorologista-chefe',required:true}],
    redes:[{key:'sigla',label:'Sigla',required:true},{key:'descricao',label:'Descrição',required:true},{key:'dmc_id',label:'Distrito de monitoramento (DMC)',required:true,options:'dmcs'},{key:'meteorologista_responsavel_id',label:'Técnico responsável',options:'tecnicos'},{key:'tipos_monitoramento',label:'Tipos de monitoramento',required:true,multi:true}],
    tecnicos:[{key:'matricula',label:'Matrícula',required:true},{key:'nome_completo',label:'Nome completo',required:true},{key:'email_contato',label:'E-mail',required:true},{key:'telefone_contato',label:'Telefone'},{key:'dmc_id',label:'Distrito de monitoramento (DMC)',required:true,options:'dmcs'},{key:'usuario_id',label:'Conta do colaborador',required:true,options:'usuarios'}],
    estacoes:[{key:'sigla',label:'Sigla',required:true},{key:'nome',label:'Nome',required:true},{key:'tipo',label:'Tipo',required:true,options:'tiposEstacao'},{key:'capacidade_maxima',label:'Capacidade máxima',required:true},{key:'latitude',label:'Latitude',required:true},{key:'longitude',label:'Longitude',required:true},{key:'rede_id',label:'Rede',required:true,options:'redes'},{key:'descricao_complementar',label:'Descrição'},{key:'sensores',label:'Sensores disponíveis',multi:true}]
  };
  get cadastroCampos():CampoCadastro[] {return this.camposPorTipo[this.cadastroKey] || [];}
  opcoesCampo(campo:CampoCadastro):CadastroItem[] {if(campo.options==='tiposEstacao')return ['AUTOMATICA','CONVENCIONAL','RADAR_METEOROLOGICO','BOIA_OCEANOGRAFICA'].map(x=>({id:x,status:'',nome:this.rotulo(x)}));return this.opcoes[campo.options || ''] || [];}
  exibirCampo(item:CadastroItem,campo:CampoCadastro):string {const valor=item[campo.key]; if(campo.options) return this.opcoesCampo(campo).find(x=>x.id===valor)?.['nome'] || this.opcoesCampo(campo).find(x=>x.id===valor)?.['nome_completo'] || this.opcoesCampo(campo).find(x=>x.id===valor)?.['descricao'] || this.opcoesCampo(campo).find(x=>x.id===valor)?.['sigla'] || String(valor||'—'); return Array.isArray(valor)?valor.map(x=>this.rotulo(String(x))).join(', '):this.rotulo(String(valor??'—'));}
  async openCadastro(key:string) {this.section='cadastro';this.cadastroKey=key;this.cadastroLabel=this.cadastros.find(x=>x.key===key)?.label||key;this.showCadastroForm=false;this.errorMessage='';await this.loadCadastro();}
  async loadCadastro() {try {for(const key of ['ogms','dmcs','redes']) {this.opcoes[key]=await firstValueFrom(this.http.get<CadastroItem[]>(this.isAdmin ? `/api/v1/admin/cadastros/${key}` : `/api/v1/catalogos/${key}`));} if(this.isAdmin){this.opcoes['tecnicos']=await firstValueFrom(this.http.get<CadastroItem[]>('/api/v1/admin/cadastros/tecnicos'));const pagina=await firstValueFrom(this.http.get<UsuarioPage>('/api/v1/admin/usuarios?size=100'));this.opcoes['usuarios']=pagina.content.filter(u=>u.perfis.includes('COLABORADOR')).map(u=>({...u,status:u.ativo?'ATIVO':'INATIVO'}));} this.cadastroItems=await firstValueFrom(this.http.get<CadastroItem[]>(this.cadastroKey==='estacoes'?'/api/v1/estacoes':`/api/v1/admin/cadastros/${this.cadastroKey}`));}catch(e){this.errorMessage=this.errorText(e,'Erro ao carregar cadastros.');}}
  newCadastro(){this.softwareEdit={tipo:'SOFTWARE_LIVRE'};this.softwareVariaveis='';this.softwaresEdit=[];this.editingId='';this.cadastroForm={};this.showCadastroForm=true;this.errorMessage='';this.successMessage='';}
  async editCadastro(item:CadastroItem){if(this.cadastroKey==='estacoes')item=await firstValueFrom(this.http.get<CadastroItem>(`/api/v1/estacoes/${item.id}`));this.softwareEdit={tipo:'SOFTWARE_LIVRE'};this.softwareVariaveis='';this.softwaresEdit=item['softwares']||[];this.editingId=item.id;this.cadastroForm={};for(const c of this.cadastroCampos)this.cadastroForm[c.key]=Array.isArray(item[c.key])?(c.key==='softwares'?JSON.stringify(item[c.key]):item[c.key].join(',')):String(item[c.key]??'');this.showCadastroForm=true;}
  async saveCadastro(){this.busy=true;this.errorMessage='';try {const url=this.cadastroKey==='estacoes'?'/api/v1/estacoes':`/api/v1/admin/cadastros/${this.cadastroKey}`; const payload:Record<string,any>={...this.cadastroForm};for(const k of ['sensores','tipos_monitoramento'])if(k in payload)payload[k]=String(payload[k]).split(',').map(x=>x.trim()).filter(Boolean);if(this.cadastroKey==='estacoes'){payload['softwares']=this.softwaresEdit;payload['sensores']=payload['sensores']||[];} if(this.editingId)await firstValueFrom(this.http.put(`${url}/${this.editingId}`,payload));else await firstValueFrom(this.http.post(url,payload));this.successMessage='Registro salvo com sucesso.';this.showCadastroForm=false;await this.loadCadastro();}catch(e){this.errorMessage=this.errorText(e,'Erro ao salvar registro. Verifique campos duplicados e vínculos.');}finally{this.busy=false;}}
  async inativarCadastro(item:CadastroItem){const motivo=await this.pedirConfirmacao('Inativar registro','O registro ficará inativo. O histórico será preservado.','Inativar',true);if(!motivo)return;this.errorMessage='';try{await firstValueFrom(this.http.patch(this.cadastroKey==='estacoes'?`/api/v1/estacoes/${item.id}/status`:`/api/v1/admin/cadastros/${this.cadastroKey}/${item.id}/inativar`,this.cadastroKey==='estacoes'?{status:'INATIVA',motivo}:{motivo}));this.successMessage='Registro inativado.';await this.loadCadastro();}catch(e){this.errorMessage=this.errorText(e,'Não foi possível inativar.');}}

  async liberarReserva(x:CadastroItem){
    if(!await this.pedirConfirmacao('Liberar estação','Confirme que o uso terminou para disponibilizar a estação novamente.','Liberar estação'))return;
    try{await firstValueFrom(this.http.patch(`/api/v1/solicitacoes/uso/${x.id}/liberar`,{}));this.successMessage='Estação liberada.';await this.openSolicitacoes('uso');}
    catch(e){this.errorMessage=this.errorText(e,'Não foi possível liberar a estação.');}
  }
  solicitacaoTipo='uso';mostrarSolicitacao=false;solicitacoesLista:CadastroItem[]=[];estacoesLista:CadastroItem[]=[];perfilFinal:CadastroItem[]=[];
  solicitacaoForm:Record<string,string>={item_tipo:'SENSOR',sensor:'TEMPERATURA'};perfilForm:Record<string,string>={};
  sensores=['TEMPERATURA','UMIDADE','PRESSAO','VENTO','PLUVIOMETRO'];
  softwareCampos=[{key:'nome_software',label:'Nome do software'},{key:'url_download',label:'Link para download'},{key:'tipo_software',label:'Licença do software'},{key:'objetivo_uso',label:'Objetivo de uso'},{key:'variaveis_monitoradas',label:'Variáveis monitoradas'}];
  get relatorios(){return this.isAdmin?['ogms','dmcs','redes','tecnicos','estacoes','uso','instalacoes']:this.isDecisor?['estacoes','uso','instalacoes']:['uso','instalacoes'];}
  get isDecisor(){return this.roles.includes('ADMINISTRADOR')||this.roles.includes('COLABORADOR');}
  nomeEstacao(id:string){return this.estacoesLista.find(x=>x.id===id)?.['nome']||id;}
  async openSolicitacoes(tipo:string){this.section='solicitacoes';this.solicitacaoTipo=tipo;this.errorMessage='';this.successMessage='';this.mostrarSolicitacao=false;try{const [sol,est,pf,dmcs]=await Promise.all([firstValueFrom(this.http.get<CadastroItem[]>(`/api/v1/solicitacoes/${tipo}`)),firstValueFrom(this.http.get<CadastroItem[]>('/api/v1/estacoes')),firstValueFrom(this.http.get<CadastroItem[]>('/api/v1/solicitacoes/meu-perfil')),firstValueFrom(this.http.get<CadastroItem[]>('/api/v1/catalogos/dmcs'))]);this.solicitacoesLista=sol;this.estacoesLista=est;this.perfilFinal=pf;this.opcoes['dmcs']=dmcs;}catch(e){this.errorMessage=this.errorText(e,'Não foi possível carregar solicitações.');}}
  async salvarPerfil(){try{await firstValueFrom(this.http.post('/api/v1/solicitacoes/meu-perfil',this.perfilForm));this.successMessage='Perfil salvo.';await this.openSolicitacoes(this.solicitacaoTipo);}catch(e){this.errorMessage=this.errorText(e,'Falha ao salvar perfil.');}}
  async enviarSolicitacao(){try{await firstValueFrom(this.solicitacaoEditId?this.http.put(`/api/v1/solicitacoes/${this.solicitacaoTipo}/${this.solicitacaoEditId}`,this.solicitacaoForm):this.http.post(`/api/v1/solicitacoes/${this.solicitacaoTipo}`,this.solicitacaoForm));this.solicitacaoEditId='';this.successMessage='Solicitação registrada.';this.mostrarSolicitacao=false;await this.openSolicitacoes(this.solicitacaoTipo);}catch(e){this.errorMessage=this.errorText(e,'Falha ao solicitar.');}}
  async decidir(x:CadastroItem,status:string){const justificativa=status==='REPROVADA' ? await this.pedirConfirmacao('Reprovar solicitação','Informe o motivo da reprovação para o solicitante.','Reprovar',true,'Justificativa') || '' : '';if(status!=='REPROVADA'&&!await this.pedirConfirmacao(status==='INSTALADA'?'Concluir instalação':'Aprovar solicitação',status==='INSTALADA'?'Confirme que o item foi instalado na estação.':'Deseja aprovar este pedido?','Confirmar'))return;if(status==='REPROVADA'&&!justificativa)return;try{await firstValueFrom(this.http.patch(`/api/v1/solicitacoes/${this.solicitacaoTipo}/${x.id}/decisao`,{status,justificativa}));await this.openSolicitacoes(this.solicitacaoTipo);}catch(e){this.errorMessage=this.errorText(e,'Falha ao decidir.');}}
  async baixarRelatorio(tipo:string,formato:string='csv'){try{const blob=await firstValueFrom(this.http.get(`/api/v1/relatorios/${tipo}.${formato}`,{responseType:'blob'}));const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`clima-${tipo}.${formato}`;a.click();URL.revokeObjectURL(url);}catch(e){this.errorMessage=this.errorText(e,'Falha na exportação.');}}
  softwaresEdit:any[]=[];softwareEdit:any={tipo:'SOFTWARE_LIVRE'};softwareVariaveis='';
  adicionarSoftware(){if(!this.softwareEdit.nome||!this.softwareEdit.url_download||!this.softwareEdit.objetivo_uso||!this.softwareVariaveis.trim()){this.errorMessage='Preencha nome, download, objetivo e variáveis do software.';return;}this.softwaresEdit.push({...this.softwareEdit,variaveis_monitoradas:this.softwareVariaveis.split(',').map(x=>x.trim()).filter(Boolean)});this.softwareEdit={tipo:'SOFTWARE_LIVRE'};this.softwareVariaveis='';this.errorMessage='';}
  dashboard={estacoes:0,redes:0,pendentes:0};
  auditoria:CadastroItem[]=[];auditPage=0;editingUserId='';solicitacaoEditId='';
  get isUsuario(){return this.roles.includes('USUARIO')||this.isAdmin;}
  async loadDashboard(){try{const [est,redes,uso,inst]=await Promise.all([firstValueFrom(this.http.get<any[]>('/api/v1/estacoes')),firstValueFrom(this.http.get<any[]>('/api/v1/catalogos/redes')),firstValueFrom(this.http.get<any[]>('/api/v1/solicitacoes/uso')),firstValueFrom(this.http.get<any[]>('/api/v1/solicitacoes/instalacao'))]);this.dashboard={estacoes:est.length,redes:redes.length,pendentes:[...uso,...inst].filter(x=>x.status==='PENDENTE').length};}catch(e){this.errorMessage=this.errorText(e,'Falha ao atualizar painel.');}}
  async loadAudit(){this.section='auditoria';try{this.auditoria=await firstValueFrom(this.http.get<CadastroItem[]>(`/api/v1/admin/auditoria?page=${this.auditPage}`));}catch(e){this.errorMessage=this.errorText(e,'Falha ao consultar auditoria.');}}
  editUser(u:Usuario){this.editingUserId=u.id;this.newUser={nome:u.nome,email:u.email,senha:'',perfis:[...u.perfis]};this.showCreate=true;}
  async inativarUser(u:Usuario){if(!await this.pedirConfirmacao('Inativar usuário','Esta pessoa perderá o acesso. Seus registros serão preservados.','Inativar'))return;try{await firstValueFrom(this.http.patch(`/api/v1/admin/usuarios/${u.id}/inativar`,{}));await this.loadUsers();}catch(e){this.errorMessage=this.errorText(e,'Falha ao inativar conta.');}}
  editarSolicitacao(x:CadastroItem){this.solicitacaoEditId=x.id;this.solicitacaoForm={};for(const [k,v] of Object.entries(x))this.solicitacaoForm[k]=Array.isArray(v)?v.join(','):String(v??'');this.mostrarSolicitacao=true;}
  async inativarSolicitacao(x:CadastroItem){const motivo=await this.pedirConfirmacao('Inativar solicitação','Informe por que deseja inativar este pedido.','Inativar',true);if(!motivo)return;try{await firstValueFrom(this.http.patch(`/api/v1/solicitacoes/${this.solicitacaoTipo}/${x.id}/inativar`,{motivo}));await this.openSolicitacoes(this.solicitacaoTipo);}catch(e){this.errorMessage=this.errorText(e,'Falha ao inativar solicitação.');}}
  async coordenadorias(item:CadastroItem){this.orgaoCoordenadorias=item;this.coordForm={id:'',nome:'',nomeado:''};this.coordErro='';try{this.listaCoordenadorias=await firstValueFrom(this.http.get<CadastroItem[]>(`/api/v1/admin/cadastros/ogms/${item.id}/coordenadorias`));}catch(e){this.coordErro=this.errorText(e,'Não foi possível carregar as coordenadorias.');}}
  async salvarCoordenadoria(){if(!this.orgaoCoordenadorias)return;this.busy=true;this.coordErro='';try{const url=`/api/v1/admin/cadastros/ogms/${this.orgaoCoordenadorias.id}/coordenadorias`;const body={nome:this.coordForm.nome,nomeado:this.coordForm.nomeado};await firstValueFrom(this.coordForm.id?this.http.put(url+'/'+this.coordForm.id,body):this.http.post(url,body));this.listaCoordenadorias=await firstValueFrom(this.http.get<CadastroItem[]>(url));this.coordForm={id:'',nome:'',nomeado:''};}catch(e){this.coordErro=this.errorText(e,'Não foi possível salvar a coordenadoria.');}finally{this.busy=false;}}
  papeis=['USUARIO','COLABORADOR','ADMINISTRADOR'];
  variaveis=['TEMPERATURA','UMIDADE','PRESSAO','VENTO','PRECIPITACAO'];
  tiposMonitoramento=['TEMPERATURA','UMIDADE','PRESSAO','VENTO','PRECIPITACAO','RADAR','CONVENCIONAL','OCEANOGRAFICO','ALERTA_DESLIZAMENTO'];
  orgaoCoordenadorias:CadastroItem|null=null;listaCoordenadorias:CadastroItem[]=[];coordErro='';coordForm={id:'',nome:'',nomeado:''};
  dialog:{titulo:string;mensagem:string;acao:string;comMotivo:boolean;rotulo:string}|null=null;dialogTexto='';private resolverDialog:((v:string|null)=>void)|null=null;
  pedirConfirmacao(titulo:string,mensagem:string,acao:string,comMotivo=false,rotulo='Motivo'):Promise<string|null>{this.dialogTexto='';this.dialog={titulo,mensagem,acao,comMotivo,rotulo};return new Promise(resolve=>this.resolverDialog=resolve);}
  fecharDialog(valor:string|null){if(valor!==null&&this.dialog?.comMotivo&&!valor.trim())return;const resolver=this.resolverDialog;this.dialog=null;this.resolverDialog=null;resolver?.(valor?.trim()||null);}
  get selecoesValidas(){if(this.cadastroKey==='redes'&&!this.cadastroForm['tipos_monitoramento'])return false;if(this.cadastroKey==='estacoes'&&['AUTOMATICA','RADAR_METEOROLOGICO'].includes(this.cadastroForm['tipo'])&&!this.cadastroForm['sensores'])return false;return true;}
  escolhas(key:string){return key==='sensores'?this.sensores:this.tiposMonitoramento;}
  selecionado(val:string|undefined,key:string){return (val||'').split(',').includes(key);}
  alternar(val:string|undefined,key:string){const lista=(val||'').split(',').filter(Boolean);return (lista.includes(key)?lista.filter(x=>x!==key):[...lista,key]).join(',');}
  novoUsuario(){this.editingUserId='';this.newUser={nome:'',email:'',senha:'',perfis:['USUARIO']};this.showCreate=!this.showCreate;this.errorMessage='';}
  togglePerfil(papel:string){this.newUser.perfis=this.newUser.perfis.includes(papel)?this.newUser.perfis.filter(x=>x!==papel):[...this.newUser.perfis,papel];}
  novaSolicitacao(){this.solicitacaoEditId='';this.solicitacaoForm={item_tipo:'SENSOR',sensor:'TEMPERATURA',tipo_software:'SOFTWARE_LIVRE'};this.mostrarSolicitacao=!this.mostrarSolicitacao;this.errorMessage='';}
  nomesPerfis(perfis:string[]){return perfis.map(x=>this.rotulo(x)).join(' · ');}
  tipoCampo(key:string){return ['latitude','longitude','capacidade_maxima'].includes(key)?'number':key.includes('email')?'email':'text';}
  ajudaCampo(key:string){return ({capacidade_maxima:'Quantidade máxima de variáveis ou capacidade de armazenamento suportada.',latitude:'De −90 a 90. Exemplo: −12,9714.',longitude:'De −180 a 180. Exemplo: −38,5014.',usuario_id:'Selecione a conta de quem será responsável pelas estações deste distrito.',sensores:'Selecione os sensores disponíveis. Obrigatório para estações automáticas e radares.',tipos_monitoramento:'Selecione tudo o que esta rede monitora.'} as Record<string,string>)[key]||'';}
  rotulo(valor:string):string{return ({USUARIO:'Usuário',COLABORADOR:'Colaborador',ADMINISTRADOR:'Administrador',AUTOMATICA:'Automática',CONVENCIONAL:'Convencional',RADAR_METEOROLOGICO:'Radar meteorológico',BOIA_OCEANOGRAFICA:'Boia oceanográfica',TEMPERATURA:'Temperatura',UMIDADE:'Umidade',PRESSAO:'Pressão atmosférica',VENTO:'Vento',PLUVIOMETRO:'Pluviômetro',PRECIPITACAO:'Precipitação',RADAR:'Radar',OCEANOGRAFICO:'Oceanográfico',ALERTA_DESLIZAMENTO:'Alerta de deslizamento',PROPRIETARIO:'Proprietário',SOFTWARE_LIVRE:'Software livre',SENSOR:'Sensor',SOFTWARE:'Software',ATIVO:'Ativo',INATIVO:'Inativo',DISPONIVEL:'Disponível',INDISPONIVEL:'Indisponível',INATIVA:'Inativa',MANUTENCAO:'Em manutenção',PENDENTE:'Aguardando análise',APROVADA:'Aprovada',REPROVADA:'Reprovada',FINALIZADA:'Uso finalizado',CANCELADA:'Cancelada',INSTALADA:'Instalação concluída',ogms:'Órgãos gestores',dmcs:'Distritos de monitoramento',redes:'Redes de monitoramento',tecnicos:'Técnicos',estacoes:'Estações meteorológicas',uso:'Solicitações de uso',instalacoes:'Solicitações de instalação',CREATE:'Cadastro',UPDATE:'Atualização',INACTIVATE:'Inativação',APPROVE:'Aprovação',REJECT:'Reprovação',RELEASE:'Liberação',INSTALL:'Instalação',LOGIN:'Entrada na conta',ACCESS_DENIED:'Acesso negado',DELETE:'Exclusão',ogm:'Órgão gestor',dmc:'Distrito de monitoramento',rede_monitoramento:'Rede de monitoramento',tecnico:'Técnico',estacao_meteorologica:'Estação meteorológica',solicitacao_uso_estacao:'Solicitação de uso',solicitacao_instalacao:'Solicitação de instalação',perfil_usuario_final:'Dados do solicitante',usuario:'Usuário'} as Record<string,string>)[valor]||valor;}
  constructor(private http: HttpClient) {}
  ngOnInit() { if (this.loggedIn) { this.loadMe(); this.loadDashboard(); if (this.isAdmin) this.loadUsers(); } }
  get isAdmin() { return this.roles.includes('ADMINISTRADOR'); }

  async login() {
    this.errorMessage = ''; this.successMessage = ''; this.busy = true;
    try {
      const response = await firstValueFrom(this.http.post<LoginResponse>('/api/v1/auth/login', { email: this.email, senha: this.password }));
      sessionStorage.setItem('clima_token', response.token); sessionStorage.setItem('clima_email', response.email); sessionStorage.setItem('clima_roles', JSON.stringify(response.perfis));
      this.loggedIn = true; this.currentEmail = response.email; this.roles = response.perfis; this.password = ''; this.section = 'dashboard';
      await this.loadMe(); await await this.loadDashboard(); if (this.isAdmin) await this.loadUsers();
    } catch (error) { this.errorMessage = this.errorText(error, 'Não foi possível entrar. Confira o e-mail e a senha e tente novamente.'); }
    finally { this.busy = false; }
  }
  logout() { sessionStorage.removeItem('clima_token'); sessionStorage.removeItem('clima_email'); sessionStorage.removeItem('clima_roles'); this.loggedIn = false; this.currentUser = null; this.users = []; this.section = 'dashboard'; this.errorMessage = ''; this.successMessage = ''; }
  async loadMe() {
    try { this.currentUser = await firstValueFrom(this.http.get<{id:string;nome:string;email:string;ativo:boolean;perfis:string[]}>('/api/v1/auth/me')); this.currentEmail = this.currentUser.email; this.roles = this.currentUser.perfis; sessionStorage.setItem('clima_roles', JSON.stringify(this.roles)); }
    catch (error) { this.errorMessage = this.errorText(error, 'Falha ao consultar o perfil.'); }
  }
  async loadUsers() {
    if (!this.isAdmin) return;
    this.errorMessage = '';
    try { const params = new URLSearchParams({ page: '0', size: '100' }); if (this.filterText.trim()) params.set('filtro', this.filterText.trim()); const page = await firstValueFrom(this.http.get<UsuarioPage>(`/api/v1/admin/usuarios?${params.toString()}`)); this.users = page.content || []; this.totalUsers = page.totalElements ?? this.users.length; this.userCount = this.totalUsers; }
    catch (error) { this.errorMessage = this.errorText(error, 'Não foi possível carregar os usuários. Seu perfil precisa ser ADMINISTRADOR.'); }
  }
  async createUser() {
    this.errorMessage = ''; this.successMessage = ''; this.busy = true;
    try { const body = { ...this.newUser }; if(this.editingUserId) await firstValueFrom(this.http.put<Usuario>(`/api/v1/admin/usuarios/${this.editingUserId}`,body));else await firstValueFrom(this.http.post<Usuario>('/api/v1/admin/usuarios',body));this.editingUserId=''; this.successMessage = 'Usuário cadastrado com sucesso.'; this.showCreate = false; this.newUser = { nome: '', email: '', senha: '', perfis: ['USUARIO'] }; this.selectedRole = 'USUARIO'; await this.loadUsers(); }
    catch (error) { this.errorMessage = this.errorText(error, 'Não foi possível cadastrar o usuário.'); }
    finally { this.busy = false; }
  }
  private errorText(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) return 'Não foi possível carregar as informações. Verifique sua conexão e tente novamente.';
      if (error.status === 401) return 'E-mail ou senha incorretos, ou seu acesso expirou. Entre novamente.';
      if (error.status === 403) return 'Você não tem permissão para realizar esta ação.';
      if (typeof error.error?.mensagem==='string' && !/SQL|JDBC|Postgre|backend|frontend|proxy|token|stack|exception/i.test(error.error.mensagem)) return error.error.mensagem;
      if (error.status === 400) return 'Dados inválidos. Confira os campos e as regras de validação.';
      return fallback;
    }
    return fallback;
  }
}


"""Teste funcional: cria dados de demonstração em um ambiente de desenvolvimento.
Configure CLIMA_TEST_URL, CLIMA_TEST_ADMIN_EMAIL e CLIMA_TEST_ADMIN_PASSWORD.
Execute somente em banco de testes: os registros são preservados para auditoria.
"""
import json, os, uuid, urllib.request, urllib.error
from datetime import date, timedelta
BASE=os.environ.get('CLIMA_TEST_URL','http://localhost:4200')
SENHA=os.environ['CLIMA_TEST_ADMIN_PASSWORD']
PREFIXO='teste-'+uuid.uuid4().hex[:8]
HEADERS={'Content-Type':'application/json'}
if os.environ.get('FRONTEND_SHARED_KEY'):
 HEADERS.update({'X-Frontend-Key':os.environ['FRONTEND_SHARED_KEY'],'Origin':os.environ.get('FRONTEND_ORIGIN','http://localhost:4200')})
checks=0

def req(method,path,body=None,token=None,expected=200,raw=False):
 global checks
 headers=dict(HEADERS)
 if token:headers['Authorization']='Bearer '+token
 r=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
 try:
  with urllib.request.urlopen(r,timeout=30) as result:code,data=result.status,result.read()
 except urllib.error.HTTPError as e:code,data=e.code,e.read()
 assert code==expected,(method,path,'esperado',expected,'recebido',code,data[:400])
 checks+=1
 return data if raw else json.loads(data) if data else None

def login(email,password=SENHA):return req('POST','/api/v1/auth/login',{'email':email,'senha':password})['token']
admin=login(os.environ.get('CLIMA_TEST_ADMIN_EMAIL','admin@clima.local'))
def novo(nome,perfis):return req('POST','/api/v1/admin/usuarios',{'nome':nome,'email':nome+'@teste.local','senha':SENHA,'perfis':perfis},admin,201)
col=novo(PREFIXO+'-col',['COLABORADOR','USUARIO']);u=novo(PREFIXO+'-u',['USUARIO']);outro=novo(PREFIXO+'-outro',['USUARIO'])
tc=login(col['email']);tu=login(u['email']);to=login(outro['email'])
dmcs=req('GET','/api/v1/catalogos/dmcs',token=tu);redes=req('GET','/api/v1/catalogos/redes',token=tu);rede=redes[0];dmc=rede['dmc_id']
tecnico=req('POST','/api/v1/admin/cadastros/tecnicos',{'matricula':PREFIXO,'nome_completo':'Técnico Teste','email_contato':col['email'],'dmc_id':dmc,'usuario_id':col['id']},admin,201)
estbody={'sigla':PREFIXO,'nome':'Estação Teste','tipo':'AUTOMATICA','rede_id':rede['id'],'capacidade_maxima':10,'latitude':-12.97,'longitude':-38.50,'sensores':['TEMPERATURA'],'softwares':[]}
est=req('POST','/api/v1/estacoes',estbody,tc,201);eid=est['id'];assert est['status']=='DISPONIVEL'
req('POST','/api/v1/estacoes',{**estbody,'sigla':PREFIXO+'-proibida'},tu,403)
for token,nome,doc in [(tu,'Usuário Teste',PREFIXO+'u'),(to,'Outro Usuário',PREFIXO+'o')]:req('POST','/api/v1/solicitacoes/meu-perfil',{'nome':nome,'documento':doc,'dmc_id':dmc},token)
body={'estacao_id':eid,'data_uso':str(date.today()+timedelta(days=2)),'hora_inicial':'07:00','hora_final':'09:30'}
uso=req('POST','/api/v1/solicitacoes/uso',body,tu,201);sid=uso['id']
req('PUT','/api/v1/solicitacoes/uso/'+sid,body,to,403)
req('PUT','/api/v1/solicitacoes/uso/'+sid,{**body,'hora_final':'10:00'},tu)
req('PATCH',f'/api/v1/solicitacoes/uso/{sid}/decisao',{'status':'APROVADA'},tc)
req('POST','/api/v1/solicitacoes/uso',body,tu,422)
req('PATCH',f'/api/v1/solicitacoes/uso/{sid}/liberar',{},to,403)
req('PATCH',f'/api/v1/solicitacoes/uso/{sid}/liberar',{},tu)
assert req('GET','/api/v1/estacoes/'+eid,token=tu)['status']=='DISPONIVEL'
s2=req('POST','/api/v1/solicitacoes/uso',body,tu,201)['id']
req('PATCH',f'/api/v1/solicitacoes/uso/{s2}/decisao',{'status':'REPROVADA'},tc,400)
req('PATCH',f'/api/v1/solicitacoes/uso/{s2}/decisao',{'status':'REPROVADA','justificativa':'Manutenção programada'},tc)
s3=req('POST','/api/v1/solicitacoes/uso',body,tu,201)['id']
req('PATCH',f'/api/v1/solicitacoes/uso/{s3}/inativar',{'motivo':'Desistência'},tu)
soutro=req('POST','/api/v1/solicitacoes/uso',body,to,201)['id']
instbody={'estacao_id':eid,'item_tipo':'SOFTWARE','nome_software':'Coletor Teste','url_download':'https://example.org/coletor','tipo_software':'SOFTWARE_LIVRE','objetivo_uso':'Coletar temperatura','variaveis_monitoradas':'TEMPERATURA,UMIDADE'}
inst=req('POST','/api/v1/solicitacoes/instalacao',instbody,tu,201)['id']
req('PUT','/api/v1/solicitacoes/instalacao/'+inst,instbody,tu)
req('PATCH',f'/api/v1/solicitacoes/instalacao/{inst}/decisao',{'status':'APROVADA'},tc)
req('PATCH',f'/api/v1/solicitacoes/instalacao/{inst}/decisao',{'status':'INSTALADA'},tc)
software=req('GET','/api/v1/estacoes/'+eid,token=tu)['softwares'][0];assert software['variaveis_monitoradas']==['TEMPERATURA','UMIDADE']
lista=req('GET','/api/v1/solicitacoes/uso',token=tu);assert sid in [s['id'] for s in lista] and soutro not in [s['id'] for s in lista]
csv=req('GET','/api/v1/relatorios/uso.csv',token=tu,raw=True).decode('utf-8');assert sid in csv and soutro not in csv
req('GET','/api/v1/relatorios/tecnicos.csv',token=tu,expected=403)
assert req('GET','/api/v1/relatorios/ogms.pdf',token=admin,raw=True).startswith(b'%PDF')
req('PATCH',f'/api/v1/admin/cadastros/tecnicos/{tecnico["id"]}/inativar',{'motivo':'Fim do teste'},admin)
audit=req('GET','/api/v1/admin/auditoria',token=admin);assert any(x['acao']=='INACTIVATE' and x['usuario_id'] for x in audit)
req('PATCH',f'/api/v1/admin/usuarios/{col["id"]}/inativar',{},admin,204)
req('GET','/api/v1/auth/me',token=tc,expected=401)
print(f'OK: {checks} chamadas HTTP verificadas; prefixo dos dados: {PREFIXO}')

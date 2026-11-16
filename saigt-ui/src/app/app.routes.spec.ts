import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { routes } from './app.routes';
import { adminGuard } from './core/auth/admin.guard';
import { authGuard } from './core/auth/auth.guard';
import { AuthService } from './core/auth/auth.service';
import { listaValoresGuard } from './core/auth/lista-valores.guard';
import { GestaoNotificacoesComponent } from './features/administracao/notificacoes/gestao-notificacoes.component';

const AUTH_SERVICE_STUB = {
  nome: () => 'Ana Teixeira',
  email: () => 'ana.teixeira@dgterritorio.gov.pt',
  origem: () => 'AD_DGT',
  entidade: () => null,
  cargo: () => null,
  perfilInterno: () => 'MASTER',
  logout: () => of(undefined),
};

/** Ao nível do router, para apanhar um `canActivate` esquecido ou um redirect errado. */
describe('routes', () => {
  let router: Router;

  function configurar(autenticado: boolean): void {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { ...AUTH_SERVICE_STUB, estaAutenticado: () => autenticado } },
      ],
    });
    router = TestBed.inject(Router);
  }

  it('a rota raiz redireciona para /painel quando autenticado', async () => {
    configurar(true);
    await router.navigateByUrl('/');
    expect(router.url).toBe('/painel');
  });

  it.each(['/painel', '/processos', '/perfil', '/notificacoes'])(
    'encaminha para o login quando não há sessão e se navega para %s',
    async (rota) => {
      configurar(false);
      await router.navigateByUrl(rota);
      expect(router.url.startsWith('/login')).toBe(true);
    },
  );

  it('permite navegar para /painel, /processos e /perfil quando autenticado', async () => {
    configurar(true);

    await router.navigateByUrl('/painel');
    expect(router.url).toBe('/painel');

    await router.navigateByUrl('/processos');
    expect(router.url).toBe('/processos');

    await router.navigateByUrl('/perfil');
    expect(router.url).toBe('/perfil');
  });

  it.each([
    ['/processos/continuar', 'EM_CURSO', 'Continuar Processos'],
    ['/processos/corrigir', 'PARA_CORRECAO', 'Corrigir Processos'],
  ])('%s abre a listagem do grupo e não o detalhe de um processo', async (url, grupo, titulo) => {
    configurar(true);

    await router.navigateByUrl(url);

    const rota = router.routerState.snapshot.root.firstChild;
    expect(rota?.routeConfig?.path).toBe(url.slice(1));
    expect(rota?.paramMap.has('processoId')).toBe(false);
    expect(rota?.data).toEqual({ grupo, titulo });
  });

  it.each([
    ['MASTER', '/notificacoes?id=n1'],
    [null, '/notificacoes?id=n1'],
    ['TECNICO', '/painel'],
    ['VISITANTE', '/painel'],
  ])('/notificacoes com o perfil %s fica em %s', async (perfil, esperado) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { ...AUTH_SERVICE_STUB, perfilInterno: () => perfil, estaAutenticado: () => true } },
      ],
    });
    router = TestBed.inject(Router);

    await router.navigateByUrl('/notificacoes?id=n1');

    expect(router.url).toBe(esperado);
  });

  it.each([
    ['MASTER', '/administracao/configuracao-procedimentos'],
    ['TECNICO', '/painel'],
    ['VISITANTE', '/painel'],
  ])('/administracao/configuracao-procedimentos com o perfil %s fica em %s', async (perfil, esperado) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { ...AUTH_SERVICE_STUB, perfilInterno: () => perfil, estaAutenticado: () => true } },
      ],
    });
    router = TestBed.inject(Router);

    await router.navigateByUrl('/administracao/configuracao-procedimentos');

    expect(router.url).toBe(esperado);
  });

  it('termina a sessão ao navegar para /login já autenticado (ex.: recuar no browser), mas continua a mostrar o login', async () => {
    const logout = vi.fn().mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { ...AUTH_SERVICE_STUB, estaAutenticado: () => true, logout } },
      ],
    });
    router = TestBed.inject(Router);

    await router.navigateByUrl('/login');

    expect(router.url).toBe('/login');
    expect(logout).toHaveBeenCalled();
  });

  it.each(['TECNICO', 'VISITANTE', null])('a Gestão de Notificações não abre a %s: o adminGuard manda para o painel', async (perfil) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { ...AUTH_SERVICE_STUB, perfilInterno: () => perfil, estaAutenticado: () => true } },
      ],
    });
    router = TestBed.inject(Router);

    await router.navigateByUrl('/administracao/notificacoes');

    expect(router.url).toBe('/painel');
  });

  it('a Gestão de Notificações abre ao MASTER e carrega o GestaoNotificacoesComponent', async () => {
    configurar(true);

    await router.navigateByUrl('/administracao/notificacoes');

    expect(router.url).toBe('/administracao/notificacoes');
    const rota = routes.find((r) => r.path === 'administracao/notificacoes')!;
    expect(rota.canActivate).toEqual([authGuard, adminGuard]);
    expect(await rota.loadComponent!()).toBe(GestaoNotificacoesComponent);
  });

  it('sem sessão, a Gestão de Notificações manda para o login', async () => {
    configurar(false);

    await router.navigateByUrl('/administracao/notificacoes');

    expect(router.url.startsWith('/login')).toBe(true);
  });

  it.each(['MASTER', 'TECNICO'])('as Listas de Valores abrem a %s (o Técnico em leitura)', async (perfil) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { ...AUTH_SERVICE_STUB, perfilInterno: () => perfil, estaAutenticado: () => true } },
      ],
    });
    router = TestBed.inject(Router);

    await router.navigateByUrl('/administracao/lista-valores');

    expect(router.url).toBe('/administracao/lista-valores');
    expect(routes.find((r) => r.path === 'administracao/lista-valores')!.canActivate).toEqual([authGuard, listaValoresGuard]);
  });

  it.each(['VISITANTE', null])('as Listas de Valores não abrem a %s: o listaValoresGuard manda para o painel', async (perfil) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { ...AUTH_SERVICE_STUB, perfilInterno: () => perfil, estaAutenticado: () => true } },
      ],
    });
    router = TestBed.inject(Router);

    await router.navigateByUrl('/administracao/lista-valores');

    expect(router.url).toBe('/painel');
  });

  it.each(['TECNICO', 'VISITANTE', null])('a Gestão de Entidades não abre a %s: o adminGuard manda para o painel', async (perfil) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { ...AUTH_SERVICE_STUB, perfilInterno: () => perfil, estaAutenticado: () => true } },
      ],
    });
    router = TestBed.inject(Router);

    await router.navigateByUrl('/administracao/entidades');

    expect(router.url).toBe('/painel');
  });

  it.each(['TECNICO', 'VISITANTE', null])('Utilizadores e permissões não abre a %s: o adminGuard manda para o painel', async (perfil) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { ...AUTH_SERVICE_STUB, perfilInterno: () => perfil, estaAutenticado: () => true } },
      ],
    });
    router = TestBed.inject(Router);

    await router.navigateByUrl('/administracao/utilizadores');

    expect(router.url).toBe('/painel');
  });

  it('a Alteração de Owner abre ao MASTER, também com processoId na query', async () => {
    configurar(true);

    await router.navigateByUrl('/administracao/alteracao-owner?processoId=0b8f2c1e-3d4a-4b5c-8d6e-7f8091a2b3c4');

    const rota = router.routerState.snapshot.root.firstChild;
    expect(rota?.routeConfig?.path).toBe('administracao/alteracao-owner');
    expect(rota?.queryParamMap.get('processoId')).toBe('0b8f2c1e-3d4a-4b5c-8d6e-7f8091a2b3c4');
  });

  it.each(['TECNICO', 'VISITANTE', null])('a Alteração de Owner não abre a %s: o adminGuard manda para o painel', async (perfil) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { ...AUTH_SERVICE_STUB, perfilInterno: () => perfil, estaAutenticado: () => true } },
      ],
    });
    router = TestBed.inject(Router);

    await router.navigateByUrl('/administracao/alteracao-owner');

    expect(router.url).toBe('/painel');
  });
});

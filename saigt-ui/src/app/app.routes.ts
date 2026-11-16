import { Routes } from '@angular/router';
import { adminGuard } from './core/auth/admin.guard';
import { authGuard } from './core/auth/auth.guard';
import { guestGuard } from './core/auth/guest.guard';
import { notificacoesGuard } from './core/auth/notificacoes.guard';
import { plantasGuard } from './core/auth/plantas.guard';
import { listaValoresGuard } from './core/auth/lista-valores.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'login/escolha-entidade',
    loadComponent: () =>
      import('./features/login/escolha-entidade/escolha-entidade.component').then((m) => m.EscolhaEntidadeComponent)
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'painel'
  },
  {
    path: 'painel',
    canActivate: [authGuard],
    loadComponent: () => import('./features/painel/painel.component').then((m) => m.PainelComponent)
  },
  {
    path: 'processos',
    canActivate: [authGuard],
    loadComponent: () => import('./features/processos/processos.component').then((m) => m.ProcessosComponent)
  },
  {
    path: 'processos/continuar',
    canActivate: [authGuard],
    data: { grupo: 'EM_CURSO', titulo: 'Continuar Processos' },
    loadComponent: () => import('./features/processos/processos.component').then((m) => m.ProcessosComponent)
  },
  {
    path: 'processos/corrigir',
    canActivate: [authGuard],
    data: { grupo: 'PARA_CORRECAO', titulo: 'Corrigir Processos' },
    loadComponent: () => import('./features/processos/processos.component').then((m) => m.ProcessosComponent)
  },
  {
    path: 'processos/criar',
    canActivate: [authGuard],
    loadComponent: () => import('./features/processos/criar-processo/criar-processo.component').then((m) => m.CriarProcessoComponent)
  },
  {
    // Depois de `processos/criar`, senão "criar" seria lido como id.
    path: 'processos/:processoId',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/processos/processo-detalhe/processo-detalhe.component').then(
        (m) => m.ProcessoDetalheComponent,
      )
  },
  {
    path: 'processos/:processoId/validacao',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/processos/processo-vista-dgt/processo-vista-dgt.component').then(
        (m) => m.ProcessoVistaDgtComponent,
      )
  },
  {
    path: 'processos/:processoId/historico',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/processos/processo-historico/processo-historico.component').then(
        (m) => m.ProcessoHistoricoComponent,
      )
  },
  {
    path: 'perfil',
    canActivate: [authGuard],
    loadComponent: () => import('./features/perfil/perfil.component').then((m) => m.PerfilComponent)
  },
  {
    path: 'notificacoes',
    canActivate: [authGuard, notificacoesGuard],
    loadComponent: () =>
      import('./features/notificacoes/notificacoes.component').then((m) => m.NotificacoesComponent)
  },
  {
    path: 'administracao/utilizadores',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/administracao/utilizadores/utilizadores.component').then((m) => m.UtilizadoresComponent)
  },
  {
    path: 'administracao/painel',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/administracao/painel/painel-administrativo.component').then(
        (m) => m.PainelAdministrativoComponent,
      )
  },
  {
    path: 'administracao/lista-valores',
    canActivate: [authGuard, listaValoresGuard],
    loadComponent: () =>
      import('./features/administracao/lista-valores/lista-valores.component').then((m) => m.ListaValoresComponent)
  },
  {
    path: 'administracao/entidades',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/administracao/entidades/entidades.component').then((m) => m.EntidadesComponent)
  },
  {
    path: 'administracao/notificacoes',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/administracao/notificacoes/gestao-notificacoes.component').then(
        (m) => m.GestaoNotificacoesComponent,
      )
  },
  {
    path: 'administracao/workflows',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/administracao/workflows/workflows.component').then((m) => m.WorkflowsComponent)
  },
  {
    path: 'administracao/plantas',
    canActivate: [authGuard, plantasGuard],
    loadComponent: () =>
      import('./features/administracao/plantas/plantas.component').then((m) => m.PlantasComponent)
  },
  {
    path: 'administracao/configuracao-procedimentos',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/administracao/configuracao-procedimentos/configuracao-procedimentos.component').then(
        (m) => m.ConfiguracaoProcedimentosComponent,
      )
  },
  {
    // `processoId` na query salta a pesquisa; a garantia de acesso é o 403 da API.
    path: 'administracao/alteracao-owner',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/administracao/alteracao-owner/alteracao-owner.component').then(
        (m) => m.AlteracaoOwnerComponent,
      )
  },
  { path: '**', redirectTo: '' }
];

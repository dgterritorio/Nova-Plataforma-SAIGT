import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { FormularioFixo, Processo, ProcessoDetalhe, ROTULO_DESTINO } from '../../../models/processo.model';
import { ProcessoService } from '../../../services/processo.service';
import { AppShellComponent } from '../../../shared/components/app-shell.component';
import { ErrorBannerComponent } from '../../../shared/components/error-banner.component';
import { EstadoProcessoBadgeComponent } from '../../../shared/components/estado-processo-badge.component';
import { ROUTES } from '../../../shared/routes';
import { EnviarPcmComponent } from '../enviar-pcm/enviar-pcm.component';
import { ProcessoFormularioInicialComponent } from '../processo-formulario-inicial/processo-formulario-inicial.component';
import { ProcessoRecusasComponent } from '../processo-recusas/processo-recusas.component';
import { ChecklistValidacaoComponent } from '../processo-validacao/checklist-validacao.component';
import { ProcessoValidacaoComponent, ResumoSeguinte } from '../processo-validacao/processo-validacao.component';
import { ResumoRejeicaoComponent } from '../resumo-rejeicao/resumo-rejeicao.component';
import { ResumoValidadosComponent } from '../resumo-validados/resumo-validados.component';

type VistaDgt = 'revisao' | 'rejeicao' | 'validados';

const FORMULARIOS_COM_LEITURA_PROPRIA: readonly FormularioFixo[] = [
  'SGIFR',
  'RATIFICACAO',
  'SUSPENSAO_CCDR',
  'SUSPENSAO_MP_GOVERNO',
  'PRORROGACAO_GOVERNO',
];

/**
 * O passo «Formulário» do processo, em leitura, para a DGT o rever: Informação Geral, Informação Base e
 * documentos, com os «Validar» por item que `acoesDisponiveis` permitir. Quem não pode validar vê o mesmo, sem botões.
 * «Seguinte» troca a vista pelo resumo da rejeição, havendo itens rejeitados, ou pelo Resumo Validados, onde o
 * «Gravar» valida o processo. Um processo já `VALIDADO_DGT` abre logo no Resumo Validados, com «Publicar» e «Enviar PCM»;
 * este abre o diálogo de escolha do utilizador da PCM. Devolvido pela PCM com documentos por validar (`revalidacaoPcm`),
 * abre primeiro na revisão, para os validar; «Seguinte» leva depois ao Resumo Validados.
 */
@Component({
  selector: 'app-processo-vista-dgt',
  standalone: true,
  imports: [
    AppShellComponent,
    ChecklistValidacaoComponent,
    EnviarPcmComponent,
    ErrorBannerComponent,
    EstadoProcessoBadgeComponent,
    ProcessoFormularioInicialComponent,
    ProcessoRecusasComponent,
    ProcessoValidacaoComponent,
    ResumoRejeicaoComponent,
    ResumoValidadosComponent,
    RouterLink,
  ],
  styleUrls: ['./processo-vista-dgt.component.css'],
  template: `
    <app-shell breadcrumb="Processos">
      <div class="conteudo">
        <app-error-banner [erro]="erro()" />

        @if (aCarregar()) {
          <p class="estado-carregamento">A carregar…</p>
        } @else if (processo(); as p) {
          <div class="cabecalho">
            <a class="voltar" [routerLink]="rotas.PROCESSOS">
              <svg viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
                <path
                  fill-rule="evenodd"
                  d="M9.78 3.22a.75.75 0 010 1.06L6.06 8l3.72 3.72a.75.75 0 11-1.06 1.06l-4.25-4.25a.75.75 0 010-1.06l4.25-4.25a.75.75 0 011.06 0z"
                  clip-rule="evenodd"
                />
              </svg>
              Processos
            </a>
            <div class="titulo">
              <h1>{{ p.designacaoInstrumento }}</h1>
              <span class="etiqueta-tipo">{{ p.tipoInstrumento }}</span>
              <app-estado-processo-badge [estado]="p.estado" />
            </div>
            <p class="subtitulo">{{ p.nomeDinamica }}</p>
            <p class="destino-processo">Destino: {{ rotulosDestino[p.destino] }}</p>
            <a class="ver-detalhe" [routerLink]="rotas.PROCESSO_DETALHE(p.id)">Ver detalhe</a>
          </div>

          @if (vista() === 'rejeicao' && p.acoesDisponiveis.includes('rejeitar')) {
            <app-resumo-rejeicao
              [processo]="p"
              (anterior)="vista.set('revisao')"
              (rejeitado)="aposRejeitar(p.id)"
              (recarregar)="lerDetalhe()"
            />
          } @else if (vista() === 'validados') {
            <app-resumo-validados
              [processo]="p"
              (anterior)="vista.set('revisao')"
              (validado)="lerDetalhe()"
              (publicado)="lerDetalhe()"
              (recarregar)="lerDetalhe()"
              (enviarPcm)="envioPcmPedido.set(true)"
            />
            <app-enviar-pcm
              [aberto]="envioPcmPedido()"
              [processo]="p"
              (fechar)="envioPcmPedido.set(false)"
              (processoAlterado)="aposEnviarPcm($event)"
              (recarregar)="recarregarAposConflitoPcm()"
            />
          } @else {
            <app-processo-formulario-inicial
              [leitura]="true"
              [formulario]="formularioDoCartao(p.formulario)"
              [dataAssinaturaNoFormulario]="p.dataAssinaturaNoFormulario ?? false"
              [modalidadeRatificacao]="p.modalidadeRatificacao"
              [redesDefesaDisponiveis]="p.redesDefesaDisponiveis ?? []"
              [tipoInformacaoFixo]="p.tipoInformacaoFixo ?? null"
              [pedeRegiao]="p.pedeRegiao ?? false"
              [opcoesRegiao]="p.opcoesRegiao ?? []"
              [pedeCimAm]="p.pedeCimAm ?? false"
              [opcoesCimAm]="p.opcoesCimAm ?? []"
              [serieDr]="p.serieDr"
              [valoresIniciais]="p"
              [tipoInstrumento]="p.tipoInstrumento"
              [tipoProcedimento]="p.formulario === 'REN' ? p.nomeDinamica : p.nomeProcedimento"
              [designacaoInstrumento]="p.designacaoInstrumento"
              [instrumentosAbrangidos]="p.instrumentosAbrangidos"
              [territorio]="p.territorio"
              [tipoAtoNome]="p.tipoAtoNome"
              [pedeLocalizacao]="p.pedeLocalizacao"
              [pedeMotivoAlteracao]="p.pedeMotivoAlteracao"
              [pedeElementosAfetados]="p.exigeElementosAfetados"
              [dinamicaAssociada]="p.dinamicaAssociada"
            />
            <app-processo-recusas [processoId]="p.id" />
            @if (mostraChecklist()) {
              <app-checklist-validacao [processoId]="p.id" />
            }
            <app-processo-validacao [processo]="p" (processoAlterado)="lerDetalhe()" (seguinte)="abrirResumo($event)" />
          }
        }
      </div>
    </app-shell>
  `,
})
export class ProcessoVistaDgtComponent implements OnInit {
  private readonly processoService = inject(ProcessoService);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  readonly rotas = ROUTES;
  readonly rotulosDestino = ROTULO_DESTINO;

  readonly processo = signal<ProcessoDetalhe | null>(null);
  readonly aCarregar = signal(true);
  readonly erro = signal<ApiError | null>(null);
  readonly vista = signal<VistaDgt>('revisao');
  /** Pedido do «Enviar PCM» do Resumo Validados: abre o diálogo de escolha do utilizador da PCM. */
  readonly envioPcmPedido = signal(false);
  private readonly resumoValidados = viewChild(ResumoValidadosComponent);

  /** Só os formulários com campos próprios na leitura do cartão; os outros leem-se como a Informação Geral. */
  formularioDoCartao(formulario: FormularioFixo | null | undefined): FormularioFixo | null {
    return formulario && FORMULARIOS_COM_LEITURA_PROPRIA.includes(formulario) ? formulario : null;
  }

  /** Só para quem valida (Técnico e Master) e só enquanto o processo está em validação; o Visitante não a vê. */
  readonly mostraChecklist = computed(() => {
    const perfil = this.authService.perfilInterno();
    return this.processo()?.estado === 'EM_VALIDACAO_DGT' && (perfil === 'MASTER' || perfil === 'TECNICO');
  });

  ngOnInit(): void {
    const id = this.activatedRoute.snapshot.paramMap.get('processoId');
    if (!id) {
      this.aCarregar.set(false);
      return;
    }
    this.processoService.obter(id).subscribe({
      next: (processo) => {
        this.processo.set(processo);
        if (processo.estado === 'VALIDADO_DGT' && !processo.revalidacaoPcm) {
          this.vista.set('validados');
        }
        this.aCarregar.set(false);
      },
      error: (erro: ApiError) => {
        this.erro.set(erro);
        this.aCarregar.set(false);
      },
    });
  }

  /** Sem o «A carregar…» da primeira leitura: a vista fica como está até o detalhe novo chegar. */
  lerDetalhe(): void {
    const processo = this.processo();
    if (!processo) {
      return;
    }
    this.processoService.obter(processo.id).subscribe({
      next: (recarregado) => this.processo.set(recarregado),
      error: (erro: ApiError) => this.erro.set(erro),
    });
  }

  abrirResumo(resumo: ResumoSeguinte): void {
    this.vista.set(resumo === 'REJEICAO' ? 'rejeicao' : 'validados');
  }

  /** A resposta não traz `acoesDisponiveis`: o «Enviar PCM» sai já e o detalhe relido confirma o resto. */
  aposEnviarPcm(atualizado: Processo): void {
    const atual = this.processo();
    this.envioPcmPedido.set(false);
    if (!atual) {
      return;
    }
    this.processo.set({
      ...atual,
      estado: atualizado.estado,
      versao: atualizado.versao,
      pcmAtribuidoAId: atualizado.pcmAtribuidoAId,
      acoesDisponiveis: atual.acoesDisponiveis.filter((acao) => acao !== 'enviar-pcm'),
    });
    this.resumoValidados()?.anunciar('Processo enviado à PCM.');
    this.lerDetalhe();
  }

  recarregarAposConflitoPcm(): void {
    this.envioPcmPedido.set(false);
    this.lerDetalhe();
  }

  aposRejeitar(processoId: string): void {
    this.router.navigate([ROUTES.PROCESSO_DETALHE(processoId)]);
  }
}

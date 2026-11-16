import { BarChart, PieChart } from 'echarts/charts';
import { GridComponent } from 'echarts/components';
import { init, use } from 'echarts/core';
import { SVGRenderer } from 'echarts/renderers';

// Só os gráficos deste ecrã e o renderizador SVG: a biblioteca inteira não entra no bundle.
use([BarChart, PieChart, GridComponent, SVGRenderer]);

export { init };

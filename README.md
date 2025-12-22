# TelegramNotify

TelegramNotify es un plugin para servidores PaperMC que permite enviar notificaciones a un chat de Telegram cuando ocurren ciertos eventos en el servidor de Minecraft.

## Requisitos
- **Servidor PaperMC** versión 1.21.1 superior.
- **Java 21** o superior.
- Un **bot de Telegram** y el ID del chat donde se enviarán las notificaciones.

## Instalación
1. Descarga el archivo JAR compilado de TelegramNotify.
2. Coloca el archivo en la carpeta `plugins` de tu servidor PaperMC.
3. Inicia el servidor para que se genere el archivo de configuración `config.yml` en la carpeta `plugins/TelegramNotify`.
4. Detén el servidor y edita el archivo `config.yml` con el token de tu bot de Telegram y el ID del chat.
5. Vuelve a iniciar el servidor.

## Configuración
El archivo `config.yml` contiene las siguientes opciones principales:
- `bot-token`: Token de tu bot de Telegram.
- `chat-id`: ID del chat de Telegram donde se enviarán los mensajes.
- Otras opciones para personalizar los mensajes y eventos a notificar.

## Comandos
- `/telereload` — Recarga la configuración del plugin sin reiniciar el servidor. Permisos: telegramnotify.reload, por defecto solo los OP lo tienen activo.

## Funcionalidades
- Envía notificaciones a Telegram cuando:
  - Un jugador entra o sale del servidor.
  - Un jugador muere.
  - Servidor iniciado.
  - Permite recargar la configuración en caliente mediante comando.

## Permisos
- `telegramnotify.reload` — Permite usar el comando de recarga.

## Soporte
Para dudas o reportar problemas, abre un issue en el repositorio o contacta al desarrollador.

---
Versión 2.0.

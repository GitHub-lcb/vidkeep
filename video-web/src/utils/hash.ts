/** 抽样指纹的单个采样块大小：2MB（文档 7.1.2） */
const SAMPLE_CHUNK_SIZE = 2 * 1024 * 1024

/**
 * 计算文件抽样指纹（文档 7.1.2）。
 *
 * 3GB 文件全量 MD5 需要几十秒，因此只取「前 2MB + 中间 2MB + 末尾 2MB + 8 字节文件大小」
 * 拼接后做 SHA-256，得到 64 位小写十六进制字符串。
 * 该指纹只用于秒传判断；完整 MD5 由服务端在合并后计算，作为最终校验。
 *
 * 注意：Web Crypto 的 crypto.subtle 仅在安全上下文（HTTPS 或 localhost）可用。
 */
export async function sampleFingerprint(file: File): Promise<string> {
  const middleStart = Math.floor(file.size / 2)
  const slices = [
    file.slice(0, Math.min(SAMPLE_CHUNK_SIZE, file.size)),
    file.slice(middleStart, middleStart + SAMPLE_CHUNK_SIZE),
    file.slice(Math.max(0, file.size - SAMPLE_CHUNK_SIZE)),
  ]
  const buffers = await Promise.all(slices.map((slice) => slice.arrayBuffer()))

  // 三段采样字节 + 8 字节文件大小（Float64，DataView 默认大端序写入）
  const totalBytes = buffers.reduce((total, buffer) => total + buffer.byteLength, 0) + 8
  const combined = new Uint8Array(totalBytes)
  let offset = 0
  for (const buffer of buffers) {
    combined.set(new Uint8Array(buffer), offset)
    offset += buffer.byteLength
  }
  new DataView(combined.buffer, offset, 8).setFloat64(0, file.size)

  const digest = await crypto.subtle.digest('SHA-256', combined)
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
}

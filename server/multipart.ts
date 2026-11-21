import type { Request } from "express";

export interface ParsedMultipartFile {
  fieldName: string;
  fileName: string;
  mimeType: string;
  data: Buffer;
}

export interface ParsedMultipartResult {
  fields: Record<string, string>;
  files: ParsedMultipartFile[];
}

export function parseMultipartBuffer(buffer: Buffer, contentType: string): ParsedMultipartResult {
  const fields: Record<string, string> = {};
  const files: ParsedMultipartFile[] = [];

  const match = /boundary=(?:"([^"]+)"|([^;\s]+))/i.exec(contentType || "");
  if (!match) return { fields, files };
  const boundary = match[1] || match[2];
  const delimiter = Buffer.from(`--${boundary}`);
  const headerSep = Buffer.from("\r\n\r\n");

  let offset = buffer.indexOf(delimiter);
  while (offset !== -1) {
    offset += delimiter.length;
    if (buffer.subarray(offset, offset + 2).toString("utf8") === "--") {
      break;
    }
    if (buffer.subarray(offset, offset + 2).toString("utf8") === "\r\n") {
      offset += 2;
    }
    const nextOffset = buffer.indexOf(delimiter, offset);
    if (nextOffset === -1) break;

    let partEnd = nextOffset;
    if (partEnd >= 2 && buffer[partEnd - 2] === 13 && buffer[partEnd - 1] === 10) {
      partEnd -= 2;
    }

    const headerEnd = buffer.indexOf(headerSep, offset);
    if (headerEnd !== -1 && headerEnd < partEnd) {
      const rawHeaders = buffer.subarray(offset, headerEnd).toString("utf8");
      const bodyBuf = buffer.subarray(headerEnd + 4, partEnd);

      const dispLineMatch = /content-disposition:([^\r\n]+)/i.exec(rawHeaders);
      const typeMatch = /content-type:\s*([^\r\n]+)/i.exec(rawHeaders);
      if (dispLineMatch) {
        const dispLine = dispLineMatch[1];
        const nameMatch = /(?:^|[;\s])name="([^"]+)"/i.exec(dispLine);
        const fileNameMatch = /(?:^|[;\s])filename="([^"]*)"/i.exec(dispLine);
        if (nameMatch) {
          const fieldName = nameMatch[1];
          if (fileNameMatch) {
            files.push({
              fieldName,
              fileName: fileNameMatch[1] || "upload.bin",
              mimeType: typeMatch ? typeMatch[1].trim() : "application/octet-stream",
              data: Buffer.from(bodyBuf)
            });
          } else {
            fields[fieldName] = bodyBuf.toString("utf8");
          }
        }
      }
    }
    offset = nextOffset;
  }

  return { fields, files };
}

export function readMultipartRequest(req: Request, maxBytes = 15 * 1024 * 1024): Promise<ParsedMultipartResult> {
  return new Promise((resolve, reject) => {
    const contentType = String(req.headers["content-type"] || "");
    if (!contentType.toLowerCase().includes("multipart/form-data")) {
      resolve({ fields: {}, files: [] });
      return;
    }
    const chunks: Buffer[] = [];
    let total = 0;
    req.on("data", (chunk: Buffer) => {
      total += chunk.length;
      if (total > maxBytes) {
        reject(new Error("Upload exceeds maximum allowed size."));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      try {
        resolve(parseMultipartBuffer(Buffer.concat(chunks), contentType));
      } catch (err) {
        reject(err);
      }
    });
    req.on("error", reject);
  });
}

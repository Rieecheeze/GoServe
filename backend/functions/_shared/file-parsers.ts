import { PDFDocument } from 'npm:pdf-lib@1.17.1';
import { Buffer } from 'node:buffer';
import { PNG } from 'npm:pngjs@7.0.0';
import jpeg from 'npm:jpeg-js@0.4.4';
export const parsers={
  async pdf(bytes:Uint8Array){const document=await PDFDocument.load(bytes,{ignoreEncryption:false,throwOnInvalidObject:true});return {pages:document.getPageCount(),encrypted:document.isEncrypted};},
  async png(bytes:Uint8Array){const decoded=PNG.sync.read(Buffer.from(bytes),{checkCRC:true});if(!decoded.width||!decoded.height||!decoded.data.length)throw new Error('Invalid PNG');},
  async jpeg(bytes:Uint8Array,policy:{max_pixels:number}){const decoded=jpeg.decode(bytes,{useTArray:true,tolerantDecoding:false,maxResolutionInMP:policy.max_pixels/1000000});if(!decoded.width||!decoded.height||!decoded.data.length)throw new Error('Invalid JPEG');}
};

/**
 * eaglercraft-webgl.js — WebGL2 bridge helper for Eaglercraft 1.21.1
 * 
 * Maintains integer→WebGLObject maps so the Java-side LWJGL stubs
 * (which use integer IDs like desktop OpenGL) can address WebGL
 * objects that are reference-typed.
 */
(function() {
    'use strict';

    // ── ID → WebGLObject maps ───────────────────────────────────────
    var _nextId = 1;
    var _textures  = {};  // int → WebGLTexture
    var _buffers   = {};  // int → WebGLBuffer
    var _programs  = {};  // int → WebGLProgram
    var _shaders   = {};  // int → WebGLShader
	    var _fbos      = {};  // int → WebGLFramebuffer
	    var _rbos      = {};  // int → WebGLRenderbuffer
	    var _vaos      = {};  // int → WebGLVertexArrayObject
	    var _uniforms  = {};  // int → WebGLUniformLocation
	    var _shaderTypes = {};
	    var _shaderSources = {};
	    var _uniformCounter = 0;

    // Program → uniform-name → int
    var _uniformMap = {};

    function nextId() { return _nextId++; }

    window._textures = _textures;
    window._buffers = _buffers;
    window._programs = _programs;
    window._shaders = _shaders;
    window._fbos = _fbos;
    window._rbos = _rbos;
	    window._vaos = _vaos;
	    window._uniforms = _uniforms;
	    window._shaderSources = _shaderSources;

    window.__eaglerGetTexObj = function(id) {
        return id === 0 ? null : (_textures[id] || null);
    };
    window.__eaglerGetBufferObj = function(id) {
        return id === 0 ? null : (_buffers[id] || null);
    };
    window.__eaglerGetFramebufferObj = function(id) {
        return id === 0 ? null : (_fbos[id] || null);
    };
    window.__eaglerGetRenderbufferObj = function(id) {
        return id === 0 ? null : (_rbos[id] || null);
    };
    window.__eaglerGetUniformObj = function(id) {
        return _uniforms[id] || null;
    };

    function isWebGL2(gl) {
        return typeof WebGL2RenderingContext !== 'undefined' && gl instanceof WebGL2RenderingContext;
    }

    function checkGLError(section) {
        var gl = window.__eaglerGL;
        if (!gl) return;
        var err = gl.getError();
        if (err && window.__eaglerTraceGL) {
            console.warn('[Eaglercraft] WebGL error after ' + section + ': 0x' + err.toString(16));
        }
    }

    window.__eaglerTexImage2D = function(target, level, intFmt, w, h, border, fmt, type, data) {
        var gl = window.__eaglerGL;
        if (!gl) return;

        if (intFmt === 6402 && fmt === 6402) {
            if (isWebGL2(gl)) {
                if (type === 5126) {
                    intFmt = 36012;
                } else if (type === 5125) {
                    intFmt = 33190;
                } else {
                    intFmt = 33189;
                    type = 5123;
                }
            } else {
                gl.getExtension('WEBGL_depth_texture');
                if (type === 5126) {
                    type = 5123;
                }
            }
        }

        if (isWebGL2(gl) && type === 5121) {
            if (intFmt === 6408) {
                intFmt = 32856;
            } else if (intFmt === 6407) {
                intFmt = 32849;
            } else if (intFmt === 33319) {
                intFmt = 33323;
            } else if (intFmt === 6403) {
                intFmt = 33321;
            }
        }

        gl.texImage2D(target, level, intFmt, w, h, border, fmt, type, data);
        checkGLError('texImage2D ' + w + 'x' + h + ' if=' + intFmt + ' fmt=' + fmt);
    };

    // ── Initialise the context ──────────────────────────────────────
    window.__eaglerInitGL = function(canvas) {
        var gl = canvas.getContext('webgl2', {
            alpha: false,
            depth: true,
            stencil: true,
            antialias: false,
            premultipliedAlpha: false,
            preserveDrawingBuffer: false,
            powerPreference: 'high-performance'
        });
        if (!gl) {
            gl = canvas.getContext('webgl', {
                alpha: false, depth: true, stencil: true, antialias: false
            });
            if (!gl) {
                console.error('[Eaglercraft] WebGL not supported!');
                return null;
            }
            console.warn('[Eaglercraft] Falling back to WebGL1');
        }
        window.__eaglerGL = gl;
        console.log('[Eaglercraft] WebGL2 context created');
        return gl;
    };

    // ── Texture helpers ─────────────────────────────────────────────
    window.__eaglerGenTexture = function() {
        var gl = window.__eaglerGL;
        var id = nextId();
        _textures[id] = gl.createTexture();
        return id;
    };
    window.__eaglerDeleteTexture = function(id) {
        var gl = window.__eaglerGL;
        if (_textures[id]) { gl.deleteTexture(_textures[id]); delete _textures[id]; }
    };
    window.__eaglerBindTexture = function(target, id) {
        var gl = window.__eaglerGL;
        gl.bindTexture(target, id === 0 ? null : (_textures[id] || null));
    };

    // ── Buffer helpers ──────────────────────────────────────────────
    window.__eaglerGenBuffer = function() {
        var gl = window.__eaglerGL;
        var id = nextId();
        _buffers[id] = gl.createBuffer();
        return id;
    };
    window.__eaglerDeleteBuffer = function(id) {
        var gl = window.__eaglerGL;
        if (_buffers[id]) { gl.deleteBuffer(_buffers[id]); delete _buffers[id]; }
    };
    window.__eaglerBindBuffer = function(target, id) {
        var gl = window.__eaglerGL;
        gl.bindBuffer(target, id === 0 ? null : (_buffers[id] || null));
    };

    // ── Program/Shader helpers ──────────────────────────────────────
    window.__eaglerCreateProgram = function() {
        var gl = window.__eaglerGL;
        var id = nextId();
        _programs[id] = gl.createProgram();
        _uniformMap[id] = {};
        return id;
    };
    window.__eaglerDeleteProgram = function(id) {
        var gl = window.__eaglerGL;
        if (_programs[id]) { gl.deleteProgram(_programs[id]); delete _programs[id]; delete _uniformMap[id]; }
    };
	    window.__eaglerCreateShader = function(type) {
	        var gl = window.__eaglerGL;
	        var id = nextId();
	        _shaders[id] = gl.createShader(type);
	        _shaderTypes[id] = type;
	        return id;
	    };
	    window.__eaglerDeleteShader = function(id) {
	        var gl = window.__eaglerGL;
	        if (_shaders[id]) { gl.deleteShader(_shaders[id]); delete _shaders[id]; delete _shaderTypes[id]; delete _shaderSources[id]; }
	    };
	    window.__eaglerShaderSource = function(id, source) {
	        var gl = window.__eaglerGL;
	        if (_shaders[id]) {
	            source = translateShaderSource(source, _shaderTypes[id]);
	            _shaderSources[id] = source;
	            gl.shaderSource(_shaders[id], source);
	        }
	    };

	    function translateShaderSource(source, type) {
	        source = String(source || '');
	        var gl = window.__eaglerGL;
		        if (typeof WebGL2RenderingContext !== 'undefined' && gl instanceof WebGL2RenderingContext) {
		            source = source.replace(/^\s*#version\s+150\b[^\r\n]*(?:\r\n|\n|\r)?/, function() {
		                return '#version 300 es\nprecision highp float;\nprecision highp int;\n';
		            });
		            source = source.replace(/clamp\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\/\s*256\.0\s*,/g, 'clamp(vec2($1) / 256.0,');
		            source = source.replace(/(\btexCoord[0-9]+\s*=\s*)(UV[0-9]+)(\s*;)/g, '$1vec2($2)$3');
		        }
	        return source;
	    }
    window.__eaglerCompileShader = function(id) {
        var gl = window.__eaglerGL;
        if (_shaders[id]) gl.compileShader(_shaders[id]);
    };
    window.__eaglerGetShaderi = function(id, pname) {
        var gl = window.__eaglerGL;
        if (!_shaders[id]) return 0;
        return gl.getShaderParameter(_shaders[id], pname) ? 1 : 0;
    };
    window.__eaglerGetShaderInfoLog = function(id) {
        var gl = window.__eaglerGL;
        if (!_shaders[id]) return '';
        return gl.getShaderInfoLog(_shaders[id]) || '';
    };
    window.__eaglerAttachShader = function(progId, shaderId) {
        var gl = window.__eaglerGL;
        if (_programs[progId] && _shaders[shaderId]) gl.attachShader(_programs[progId], _shaders[shaderId]);
    };
    window.__eaglerLinkProgram = function(id) {
        var gl = window.__eaglerGL;
        if (_programs[id]) gl.linkProgram(_programs[id]);
    };
    window.__eaglerGetProgrami = function(id, pname) {
        var gl = window.__eaglerGL;
        if (!_programs[id]) return 0;
        var v = gl.getProgramParameter(_programs[id], pname);
        return (v === true) ? 1 : (v === false ? 0 : (v|0));
    };
    window.__eaglerGetProgramInfoLog = function(id) {
        var gl = window.__eaglerGL;
        if (!_programs[id]) return '';
        return gl.getProgramInfoLog(_programs[id]) || '';
    };
    window.__eaglerUseProgram = function(id) {
        var gl = window.__eaglerGL;
        gl.useProgram(id === 0 ? null : (_programs[id] || null));
    };
    window.__eaglerGetUniformLocation = function(progId, name) {
        var gl = window.__eaglerGL;
        if (!_programs[progId]) return -1;
        // Check cache
        var map = _uniformMap[progId];
        if (map && map[name] !== undefined) return map[name];
        var loc = gl.getUniformLocation(_programs[progId], name);
        if (loc === null) return -1;
        var id = ++_uniformCounter;
        _uniforms[id] = loc;
        if (map) map[name] = id;
        return id;
    };
    window.__eaglerGetAttribLocation = function(progId, name) {
        var gl = window.__eaglerGL;
        if (!_programs[progId]) return -1;
        return gl.getAttribLocation(_programs[progId], name);
    };
    window.__eaglerBindAttribLocation = function(progId, index, name) {
        var gl = window.__eaglerGL;
        if (_programs[progId]) gl.bindAttribLocation(_programs[progId], index, name);
    };

    // ── Uniform setters ─────────────────────────────────────────────
    window.__eaglerUniform1i = function(loc, v) {
        var gl = window.__eaglerGL;
        if (_uniforms[loc]) gl.uniform1i(_uniforms[loc], v);
    };
    window.__eaglerUniform1f = function(loc, v) {
        var gl = window.__eaglerGL;
        if (_uniforms[loc]) gl.uniform1f(_uniforms[loc], v);
    };
    window.__eaglerUniform2f = function(loc, x, y) {
        var gl = window.__eaglerGL;
        if (_uniforms[loc]) gl.uniform2f(_uniforms[loc], x, y);
    };
    window.__eaglerUniform3f = function(loc, x, y, z) {
        var gl = window.__eaglerGL;
        if (_uniforms[loc]) gl.uniform3f(_uniforms[loc], x, y, z);
    };
    window.__eaglerUniform4f = function(loc, x, y, z, w) {
        var gl = window.__eaglerGL;
        if (_uniforms[loc]) gl.uniform4f(_uniforms[loc], x, y, z, w);
    };
    window.__eaglerUniformMatrix4fv = function(loc, transpose, data) {
        var gl = window.__eaglerGL;
        if (_uniforms[loc]) gl.uniformMatrix4fv(_uniforms[loc], transpose, data);
    };
    window.__eaglerUniformMatrix3fv = function(loc, transpose, data) {
        var gl = window.__eaglerGL;
        if (_uniforms[loc]) gl.uniformMatrix3fv(_uniforms[loc], transpose, data);
    };
    window.__eaglerUniformMatrix2fv = function(loc, transpose, data) {
        var gl = window.__eaglerGL;
        if (_uniforms[loc]) gl.uniformMatrix2fv(_uniforms[loc], transpose, data);
    };

    // ── FBO helpers ─────────────────────────────────────────────────
    window.__eaglerGenFramebuffer = function() {
        var gl = window.__eaglerGL;
        var id = nextId();
        _fbos[id] = gl.createFramebuffer();
        return id;
    };
    window.__eaglerDeleteFramebuffer = function(id) {
        var gl = window.__eaglerGL;
        if (_fbos[id]) { gl.deleteFramebuffer(_fbos[id]); delete _fbos[id]; }
    };
    window.__eaglerBindFramebuffer = function(target, id) {
        var gl = window.__eaglerGL;
        gl.bindFramebuffer(target, id === 0 ? null : (_fbos[id] || null));
    };

    // ── RBO helpers ─────────────────────────────────────────────────
    window.__eaglerGenRenderbuffer = function() {
        var gl = window.__eaglerGL;
        var id = nextId();
        _rbos[id] = gl.createRenderbuffer();
        return id;
    };
    window.__eaglerDeleteRenderbuffer = function(id) {
        var gl = window.__eaglerGL;
        if (_rbos[id]) { gl.deleteRenderbuffer(_rbos[id]); delete _rbos[id]; }
    };
    window.__eaglerBindRenderbuffer = function(target, id) {
        var gl = window.__eaglerGL;
        gl.bindRenderbuffer(target, id === 0 ? null : (_rbos[id] || null));
    };

    // ── VAO helpers ─────────────────────────────────────────────────
    window.__eaglerGenVertexArray = function() {
        var gl = window.__eaglerGL;
        var id = nextId();
        _vaos[id] = gl.createVertexArray();
        return id;
    };
    window.__eaglerDeleteVertexArray = function(id) {
        var gl = window.__eaglerGL;
        if (_vaos[id]) { gl.deleteVertexArray(_vaos[id]); delete _vaos[id]; }
    };
    window.__eaglerBindVertexArray = function(id) {
        var gl = window.__eaglerGL;
        gl.bindVertexArray(id === 0 ? null : (_vaos[id] || null));
    };

    console.log('[Eaglercraft] WebGL bridge loaded');
})();

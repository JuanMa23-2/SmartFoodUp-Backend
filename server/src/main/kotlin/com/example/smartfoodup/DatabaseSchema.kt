package com.example.smartfoodup

import org.jetbrains.exposed.sql.Table

// 1. TABLA USUARIOS
object Usuarios : Table("usuarios") {
    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", 100)
    val correo = varchar("correo", 150).uniqueIndex()
    val contrasena = varchar("contrasena", 255)
    val rol = varchar("rol", 20) // "CLIENTE", "RESTAURANTE", "REPARTIDOR"

    override val primaryKey = PrimaryKey(id)
}

// 2. TABLA RESTAURANTES
object Restaurantes : Table("restaurantes") {
    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", 100)
    val direccion = varchar("direccion", 255)
    val telefono = varchar("telefono", 20)
    val imagenUrl = varchar("imagen_url", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

// 3. TABLA CATEGORIAS
object Categorias : Table("categorias") {
    val id = integer("id").autoIncrement()
    val nombre = varchar("nombre", 50)

    override val primaryKey = PrimaryKey(id)
}

// 4. TABLA PRODUCTOS
object Productos : Table("productos") {
    val id = integer("id").autoIncrement()
    val restauranteId = integer("restaurante_id").references(Restaurantes.id)
    val categoriaId = integer("categoria_id").references(Categorias.id)
    val nombre = varchar("nombre", 100)
    val descripcion = varchar("descripcion", 255).nullable()
    val precio = double("precio")
    val imagenUrl = varchar("imagen_url", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

// 5. TABLA PEDIDOS
object Pedidos : Table("pedidos") {
    val id = integer("id").autoIncrement()
    val usuarioId = integer("usuario_id").references(Usuarios.id)
    val restauranteId = integer("restaurante_id").references(Restaurantes.id)
    val total = double("total")
    val estado = varchar("estado", 30) // "PENDIENTE", "PREPARANDO", "EN_CAMINO", "ENTREGADO"
    val fecha = varchar("fecha", 50)

    override val primaryKey = PrimaryKey(id)
}
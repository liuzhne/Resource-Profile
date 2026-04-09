-- 师生资源画像系统数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS edu_portrait DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE edu_portrait;

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    avatar VARCHAR(255) DEFAULT NULL COMMENT '头像',
    email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(20) DEFAULT NULL COMMENT '电话',
    user_type TINYINT DEFAULT 0 COMMENT '用户类型：0-管理员，1-教师，2-学生',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(50) NOT NULL COMMENT '角色名称',
    code VARCHAR(50) NOT NULL COMMENT '角色编码',
    description VARCHAR(255) DEFAULT NULL COMMENT '描述',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(50) NOT NULL COMMENT '权限名称',
    code VARCHAR(100) NOT NULL COMMENT '权限编码',
    type TINYINT DEFAULT 1 COMMENT '类型：1-菜单，2-按钮',
    parent_id BIGINT DEFAULT 0 COMMENT '父ID',
    sort_order INT DEFAULT 0 COMMENT '排序',
    icon VARCHAR(50) DEFAULT NULL COMMENT '图标',
    path VARCHAR(255) DEFAULT NULL COMMENT '路由路径',
    component VARCHAR(255) DEFAULT NULL COMMENT '组件路径',
    status TINYINT DEFAULT 1 COMMENT '状态',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 教师信息表
CREATE TABLE IF NOT EXISTS teacher_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    employee_id VARCHAR(50) NOT NULL COMMENT '工号',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    gender TINYINT DEFAULT NULL COMMENT '性别：0-女，1-男',
    birth_date DATE DEFAULT NULL COMMENT '出生日期',
    dept_id BIGINT DEFAULT NULL COMMENT '学院ID',
    dept_name VARCHAR(100) DEFAULT NULL COMMENT '学院名称',
    title VARCHAR(50) DEFAULT NULL COMMENT '职称',
    education VARCHAR(50) DEFAULT NULL COMMENT '学历',
    school VARCHAR(100) DEFAULT NULL COMMENT '毕业院校',
    major VARCHAR(100) DEFAULT NULL COMMENT '专业方向',
    research_area VARCHAR(500) DEFAULT NULL COMMENT '研究方向',
    join_date DATE DEFAULT NULL COMMENT '入职日期',
    status TINYINT DEFAULT 1 COMMENT '状态：0-离职，1-在职',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_employee_id (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师信息表';

-- 学生信息表
CREATE TABLE IF NOT EXISTS student_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    student_id VARCHAR(50) NOT NULL COMMENT '学号',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    gender TINYINT DEFAULT NULL COMMENT '性别：0-女，1-男',
    birth_date DATE DEFAULT NULL COMMENT '出生日期',
    dept_id BIGINT DEFAULT NULL COMMENT '学院ID',
    dept_name VARCHAR(100) DEFAULT NULL COMMENT '学院名称',
    major_id BIGINT DEFAULT NULL COMMENT '专业ID',
    major_name VARCHAR(100) DEFAULT NULL COMMENT '专业名称',
    grade VARCHAR(20) DEFAULT NULL COMMENT '年级',
    class_name VARCHAR(50) DEFAULT NULL COMMENT '班级',
    enrollment_date DATE DEFAULT NULL COMMENT '入学日期',
    expected_graduation DATE DEFAULT NULL COMMENT '预计毕业日期',
    gpa DECIMAL(3,2) DEFAULT 0.00 COMMENT 'GPA',
    credits INT DEFAULT 0 COMMENT '已修学分',
    status TINYINT DEFAULT 1 COMMENT '状态：0-退学，1-在读，2-毕业',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_student_id (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生信息表';

-- 插入默认数据
INSERT INTO sys_user (username, password, nickname, user_type, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '管理员', 0, 1),
('teacher', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '测试教师', 1, 1),
('student', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '测试学生', 2, 1);

INSERT INTO sys_role (name, code, description) VALUES
('系统管理员', 'admin', '拥有系统所有权限'),
('教师', 'teacher', '可以查看教师画像和学生信息'),
('学生', 'student', '可以查看自己的画像信息'),
('心理健康教师', 'mental_teacher', '可以管理心理健康相关功能');

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3);

INSERT INTO sys_permission (name, code, type, parent_id, sort_order, icon, path) VALUES
('数据面板', 'dashboard', 1, 0, 1, 'DataLine', '/dashboard'),
('教师画像', 'teacher', 1, 0, 2, 'UserFilled', '/teacher'),
('教师列表', 'teacher:list', 2, 2, 1, '', '/teacher/list'),
('教师详情', 'teacher:detail', 2, 2, 2, '', '/teacher/detail'),
('学生画像', 'student', 1, 0, 3, 'Reading', '/student'),
('学生列表', 'student:list', 2, 5, 1, '', '/student/list'),
('学生详情', 'student:detail', 2, 5, 2, '', '/student/detail'),
('心理健康', 'mental', 1, 0, 4, 'FirstAidKit', '/mental'),
('心理概览', 'mental:overview', 2, 8, 1, '', '/mental/overview'),
('问卷管理', 'mental:questionnaire', 2, 8, 2, '', '/mental/questionnaire'),
('分析报告', 'mental:analysis', 2, 8, 3, '', '/mental/analysis'),
('系统管理', 'admin', 1, 0, 5, 'Setting', '/admin'),
('用户管理', 'admin:user', 2, 12, 1, '', '/admin/users'),
('角色权限', 'admin:role', 2, 12, 2, '', '/admin/roles');
